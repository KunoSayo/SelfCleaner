package io.github.euonmyoji.selfcleaner;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import io.github.euonmyoji.selfcleaner.util.Util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_EXTERNAL_STORAGE = 1;
    public static final int REQUEST_FILE_CODE = 9;
    public static final String CLEAN_ALL_SUB = "all";

    public static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY
    };
    public static MainActivity instance;
    private static LocalDateTime now;
    private final List<CleanData> cleanDataList = new ArrayList<>();
    private NotificationManager notificationManager;
    private TableLayout tableLayout;
    private Thread thread;

    private static void listAndDelete(CleanTask task, int days, Path path) throws Throwable {
        task.files += 1;
        if (Files.isDirectory(path)) {
            for (Path path1 : Files.list(path).collect(Collectors.toList())) {
                listAndDelete(task, days, path1);
            }
            if (Files.list(path).count() == 0) {
                task.shouldDeleted += 1;
                try {
                    if (Files.deleteIfExists(path)) {
                        task.deleted += 1;
                    }
                } catch (IOException ignore) {
                }
            }
        } else {
            long bytes = 0;
            try {
                bytes = Files.getFileStore(path).getTotalSpace();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            task.filesBytes += bytes;
            Duration duration = Duration.between(LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()),
                    now);
            if (duration.getSeconds() / (24 * 60 * 60 * 1.0) > days) {
                task.shouldDeleted += 1;
                task.shouldDeletedBytes += bytes;
                if (Files.deleteIfExists(path)) {
                    task.deleted += 1;
                    task.deletedBytes += bytes;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity.instance = this;
        Util.requestWRPermission();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Objects.requireNonNull(notificationManager)
                .createNotificationChannel(new NotificationChannel("ljyys", "来局友谊赛", NotificationManager.IMPORTANCE_DEFAULT));
        try {
            setContentView(R.layout.activity_scrolling);
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            CollapsingToolbarLayout toolBarLayout = findViewById(R.id.toolbar_layout);
            toolBarLayout.setTitle(getTitle());

            FloatingActionButton fab = findViewById(R.id.fab);
            fab.setOnClickListener(view -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, REQUEST_FILE_CODE);
                } catch (Throwable e) {
                    Util.showException(this, e, false);
                }
            });

            Path cfgDir = Paths.get(Environment.getExternalStorageDirectory() + "/yys/");
            try {
                Files.createDirectories(cfgDir);
                Path cfgPath = cfgDir.resolve("selfCleaner.txt");
                if (Files.notExists(cfgPath)) {
                    Files.createFile(cfgPath);
                }

                Files.lines(cfgPath).forEach(s -> {
                    String[] args = s.split(" ");
                    switch (args[0]) {
                        case CLEAN_ALL_SUB: {
                            int days = Integer.parseInt(args[1]);
                            if (args.length < 3) {
                                throw new IllegalArgumentException("Config clean args not good");
                            }
                            StringBuilder sb = new StringBuilder();
                            for (int i = 2; i < args.length - 1; ++i) {
                                sb.append(args[i]);
                                sb.append(" ");
                            }
                            sb.append(args[args.length - 1]);
                            cleanDataList.add(new CleanData(days, Paths.get(sb.toString())));
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                });
            } catch (Throwable e) {
                Util.showException(this, e, true);
            }
            findViewById(R.id.save).setOnClickListener(v -> {
                try (BufferedWriter writer = Files.newBufferedWriter(cfgDir.resolve("selfCleaner.txt"), StandardOpenOption.CREATE)) {
                    for (CleanData cleanData : cleanDataList) {
                        writer.write("all " + cleanData.days + " " + cleanData.path.toString() + "\n");
                    }
                    writer.flush();
                } catch (IOException e) {
                    Util.showException(this, e, true);
                }
            });
            findViewById(R.id.start).setOnClickListener(v -> {
                if (thread == null || !thread.isAlive()) {
                    new AlertDialog.Builder(this)
                            .setTitle("确定清理当前渲染目录的文件夹下文件？")
                            .setPositiveButton("确定", (dialog, which) -> {
                                now = LocalDateTime.now();
                                (thread = new Thread(this::clean)).start();

                            }).setNegativeButton("取消", (dialog, which) -> {
                    }).show();
                } else {
                    Toast.makeText(this, "已经有任务正在清理了", Toast.LENGTH_SHORT).show();
                }
            });
            tableLayout = findViewById(R.id.table_layout);
            for (CleanData cleanData : cleanDataList) {
                pushEvent(cleanData);
            }
        } catch (Throwable e) {
            Util.showException(this, e, false);
        }
    }

    private void clean() {
        try {
            CleanTask totalTask = new CleanTask();
            for (CleanData cleanData : cleanDataList) {
                runOnUiThread(() -> Toast.makeText(this, "开始删除" + cleanData.path.toString(), Toast.LENGTH_SHORT).show());
                try {
                    CleanTask task = new CleanTask();
                    listAndDelete(task, cleanData.days, cleanData.path);
                    totalTask.add(task);
                    String msg = "删除了" + cleanData.path.toString() + " 中 " + task.toString() + " 文件";
                    Notification notification = new Notification.Builder(this, "ljyys")
                            .setContentTitle("删除项目完成一个")
                            .setSmallIcon(R.mipmap.notification_icon)
                            .setBadgeIconType(R.mipmap.notification_icon)
                            .setContentText(msg)
                            .build();
                    notificationManager.notify(9961, notification);
                    runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
                } catch (Throwable e) {
                    Util.showException(this, e, false);
                }
            }
            String msg = "删除了共" + totalTask.toString() + " 文件";
            Notification notification = new Notification.Builder(this, "ljyys")
                    .setContentTitle("删除项目完成")
                    .setSmallIcon(R.mipmap.notification_icon)
                    .setBadgeIconType(R.mipmap.notification_icon)
                    .setContentText(msg)
                    .setAutoCancel(false)
                    .build();
            notificationManager.notify(9961, notification);
            runOnUiThread(() -> {
                try {

                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                } catch (Throwable e) {
                    Util.showException(this, e, false);
                }
            });
        } catch (Throwable e) {
            Util.showException(this, e, false);
        }
    }

    private void pushEvent(CleanData cleanData) {
        TableRow tableRow = new TableRow(this);
        tableRow.addView(new androidx.appcompat.widget.AppCompatButton(this) {{
            setText("Rm");
            setOnClickListener(v1 -> {
                tableLayout.removeView(tableRow);
                cleanDataList.remove(cleanData);
            });
        }});
        tableRow.addView(new androidx.appcompat.widget.AppCompatTextView(this) {{
            this.setText(cleanData.days + "");
        }});
        tableRow.addView(new androidx.appcompat.widget.AppCompatTextView(this) {{
            this.setText(cleanData.path.toString());
        }});
        tableLayout.addView(tableRow);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            switch (requestCode) {
                case REQUEST_FILE_CODE: {
                    if (resultCode == -1 && data != null && data.getData() != null) {
                        String path = Objects.requireNonNull(data.getData().getPath())
                                .replace("tree", "storage")
                                .replace("primary:", "emulated/0/");
                        final EditText argText = new EditText(this);
                        argText.setInputType(InputType.TYPE_CLASS_NUMBER);
                        argText.setText("2");
                        new AlertDialog.Builder(this)
                                .setTitle("删除多少天前文件?")
                                .setView(argText)
                                .setPositiveButton("确定", (dialog, which) -> {
                                    try {
                                        int inputDetailed = Integer.parseInt(argText.getText().toString());
                                        if (inputDetailed >= 0) {
                                            CleanData cleanData = new CleanData(inputDetailed, Paths.get(Objects.requireNonNull(path)));
                                            cleanDataList.add(cleanData);
                                            pushEvent(cleanData);
                                        }
                                    } catch (Throwable e) {
                                        Util.showException(this, e, true);
                                    }
                                }).setNegativeButton("取消", (dialog, which) -> {
                        }).show();
                    }
                    break;
                }
                default: {
                    super.onActivityResult(requestCode, resultCode, data);
                    break;
                }
            }
        } catch (Throwable e) {
            Util.showException(this, e, false);
        }
    }

}
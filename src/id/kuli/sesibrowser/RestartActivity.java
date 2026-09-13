package id.kuli.sesibrowser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

public class RestartActivity extends Activity {
    public static final String EXTRA_PID = "pid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        if (pid > 0) Process.killProcess(pid);
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
        Runtime.getRuntime().exit(0);
    }
}

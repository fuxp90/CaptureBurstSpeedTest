package com.cdts.synccapture;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.test_capture_speed:
                Intent intent = new Intent(this, SpeedTestActivity.class);
                startActivity(intent);
                break;
            case R.id.about:
                AlertDialog aboutDialog = new AlertDialog.Builder(this).create();
                aboutDialog.setTitle(R.string.app_name);
                aboutDialog.setMessage("Build date:" + BuildConfig.VERSION_NAME);
                aboutDialog.show();
                break;

        }
        return true;
    }
}

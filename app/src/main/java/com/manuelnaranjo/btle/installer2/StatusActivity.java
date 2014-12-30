package com.manuelnaranjo.btle.installer2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.stericson.RootTools.Command;
import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class StatusActivity extends Activity implements InstallerListener {
  static final String TAG;
  static final String PROGRESS;
  static final String COMPLETE;
  static final String DATA;

  static {
    TAG = "BTLE-Installer";
    PROGRESS = "com.manuelnaranjo.btle.installer2.PROGRESS";
    COMPLETE = "com.manuelnaranjo.btle.installer2.COMPLETE";
    DATA = "DATA";
  }

  /*
   * List of provided models
   */
  private static List<String> MODELS = Arrays.asList(
    "grouper",
    "manta");

  private static List<String> FILES = Arrays.asList(
    "libbt-vendor.so",
    "bluetooth.default.so");

  private static List<String> ASSETS = Arrays.asList(
    "grouper",
    "manta",
    "xbin",
    "install.sh"
  );

  private String mBOARD;
  private TextView mTxtDeviceBoard, mTxtApiStatus,
    mTxtBackupStatus, mTxtLog;
  private Button mBtnInstall, mBtnUninstall;

  private static final int RESULT_BUSYBOX = 1;

  private boolean mCompatible = false;
  private boolean mRootReady = false;
  private boolean mInstalled = false;
  private Boolean mReceiverRegistered = false;
  private String mFilesPath, mBusyBox;

  private BroadcastReceiver mReceiver;

  {
    mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(PROGRESS)) {
          String line = intent.getStringExtra(DATA);
          Log.v(TAG, intent.getAction() + " " + line);
          if (line != null) {
            logVerbose(line);
          }
          return;
        }

        if (intent.getAction().equals(COMPLETE)) {
          boolean val = intent.getBooleanExtra(DATA, false);
          Log.v(TAG, intent.getAction() + " " + val);
          if (val) {
            yesNoAlert("Installation completed", "Do you want to reboot now?",
              new yesNoListener() {
                @Override
                public void actionYes() {
                  try {
                    Runtime.getRuntime().exec("/system/bin/reboot");
                  } catch (IOException e) {

                    Toast.makeText(getApplicationContext(),
                      "Failed rebooting",
                      Toast.LENGTH_LONG).show();

                    Log.e(TAG, "Failed to reboot", e);
                  }
                }

                @Override
                public void actionNo() {
                  mBtnUninstall.setEnabled(true);
                }
              });
          }
        }


      }
    };
  }

  private File mBackupDir;

  public void logVerbose(String t) {
    Log.v(TAG, t);
    addToLog("V: " + t);
  }

  public void logInfo(String t) {
    Log.i(TAG, t);
    addToLog("I: " + t);
  }

  public void logError(String t) {
    logError(t, null);
  }

  public void logError(String t, Exception e) {
    Log.e(TAG, t, e);
    addToLog("E: " + t);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == RESULT_BUSYBOX) {
      logVerbose("Got result from busybox installation " + resultCode);
      if (resultCode == RESULT_OK) {
        logVerbose("Busybox was installed");
        updateValues();
      } else {
        logError("Failed to install busybox");
        Toast.makeText(getApplicationContext(),
          getResources().getString(R.string.busybox_required),
          Toast.LENGTH_LONG).show();
      }
      return;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  public String testRoot() {
    if (!RootTools.isRootAvailable()) {
      logError("Root not available");
      Toast.makeText(getApplicationContext(),
        "Your device needs to be rooted first",
        Toast.LENGTH_LONG).show();

      return getResources().getString(R.string.root_not_available);
    }

    if (!RootTools.isAccessGiven()) {
      logError("Root not granted");
      Toast.makeText(getApplicationContext(),
        "You need to provide root permissions for this app to work",
        Toast.LENGTH_LONG).show();

      return getResources().getString(R.string.root_required);
    }

    logInfo("Root ready");
    mRootReady = true;
    return getResources().getString(R.string.system_ok);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mFilesPath = "/data/data/" + this.getPackageName() + "/files/";
    mBusyBox = mFilesPath + "xbin/busybox";

    for (String path: ASSETS) {
      copyFileOrDir(path);
    }

    try {
      Runtime.getRuntime().exec("/system/bin/chmod 700 " + mBusyBox);
    } catch (IOException e) {
      Log.e(TAG, "Failed doing chmod", e);
    }

    setContentView(R.layout.activity_status);

    mTxtDeviceBoard = (TextView) this.findViewById(R.id.txtDeviceModel);
    mTxtApiStatus = (TextView) this.findViewById(R.id.txtApiStatus);
    mTxtBackupStatus = (TextView) this.findViewById(R.id.txtBackupStatus);
    mTxtLog = (TextView) this.findViewById(R.id.txtLog);
    mTxtLog.setText("");

    mBtnInstall = (Button) this.findViewById(R.id.btnInstall);
    mBtnUninstall = (Button) this.findViewById(R.id.btnUninstall);

    mBtnInstall.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        addToLog("Starting installation");
        mBtnInstall.setEnabled(false);
      }
    });

    mBtnUninstall.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View v) {
        RemoveProcess p = new RemoveProcess(StatusActivity.this);
        addToLog("Removing installation");
        p.start();
        mBtnUninstall.setEnabled(false);
      }
    });

    mBOARD = android.os.Build.BOARD;
    mTxtDeviceBoard.setText(mBOARD);
    if (!MODELS.contains(mBOARD)) {
      logError("Device is not compatible, Nexus 7 2012, Nexus 10 and Galaxy Nexus only by now");
      mBtnInstall.setEnabled(false);
      mBtnUninstall.setEnabled(false);
      return;
    }

    mCompatible = true;
    mBackupDir = new File(getFilesDir(), "backup");
    testRoot();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (mCompatible) {
      updateValues();
    }

    if (!mReceiverRegistered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(PROGRESS);
      filter.addAction(COMPLETE);
      registerReceiver(mReceiver, filter);

      mReceiverRegistered = true;
    }

  }

  @Override
  protected void onPause() {
    super.onPause();
    if (mReceiverRegistered) {
      unregisterReceiver(mReceiver);
      mReceiverRegistered = false;
    }
  }

  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
  }

  private boolean checkBackupStatus() {
    boolean r = true;

    for (String f : FILES) {
      boolean res = new File(mBackupDir, f).exists();
      logInfo("File " + f + " backup " + (res ? "found" : "not found"));
      r = res && r;
    }

    mTxtBackupStatus.setText(r ? R.string.found : R.string.not_found);

    return r;
  }

  public void updateValues() {
    this.runOnUiThread(new Runnable() {

      @Override
      public void run() {
        mInstalled = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        mTxtApiStatus.setText(mInstalled == true ? R.string.available : R.string.not_installed);
        boolean backup = checkBackupStatus();
        mBtnInstall.setEnabled(!mInstalled && mRootReady);
        mBtnUninstall.setEnabled(mInstalled && mRootReady);
      }
    });

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_status, menu);
    return true;
  }

  @Override
  public void clearLog() {
    if (mTxtLog != null)
      this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          mTxtLog.setText("");
        }
      });
  }

  @Override
  public void addToLog(final String t) {
    if (mTxtLog != null)
      this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          mTxtLog.append(t + "\n");
        }
      });
  }

  interface yesNoListener {
    void actionYes();

    void actionNo();
  }

  private void yesNoAlert(String title, String message, final yesNoListener l) {
    new AlertDialog.Builder(this).setMessage(message)
      .setTitle(title)
      .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          l.actionYes();
        }
      })
      .setNegativeButton("No", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          l.actionNo();
        }
      })
      .show();
  }

  yesNoListener rebootListener = new yesNoListener() {
    public void actionYes() {
      Command c;
      try {
        c = RootTools.getShell(true).add(new CommandCapture(0, "reboot"));
        c.exitCode();
        return;
      } catch (Exception e) {
        Log.e(TAG, "reboot error", e);
      }

      Toast.makeText(getApplicationContext(),
        "Reboot failed, you need to manually reboot",
        Toast.LENGTH_LONG).show();

    }

    public void actionNo() {
      Toast.makeText(getApplicationContext(),
        "Reboot needed before you can use this activity again",
        Toast.LENGTH_LONG).show();
      finish();
    }
  };

  @Override
  public void reboot() {
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        yesNoAlert("Reboot device?",
          "You need to reboot your device to complete this action",
          rebootListener);
      }
    });
  }

  @Override
  public File getBackupDir() {
    return mBackupDir;
  }

  @Override
  public InputStream getTargetFile(String path) {
    path = mBOARD + "/" + path;
    try {
      return getAssets().open(path);
    } catch (IOException e) {
      logError("Failed getting asset " + path);
    }
    return null;
  }

  private void copyFileOrDir(String path) {
    String assets[] = null;
    Log.v(TAG, "copyFileOrDir " + path);
    try {
      assets = getAssets().list(path);
      if (assets.length == 0) {
        copyFile(path);
      } else {
        String fullPath = mFilesPath + path;
        File dir = new File(fullPath);
        if (!dir.exists())
          dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          if (path.length() > 0) {
            copyFileOrDir(path + "/" + assets[i]);
          } else {
            copyFileOrDir(assets[i]);
          }
        }
      }
    } catch (IOException ex) {
      Log.e(TAG, "I/O Exception", ex);
    }
  }

  private void copyFile(String filename) {
    String newFileName = mFilesPath + filename;

    AssetManager assetManager = this.getAssets();
    Log.v(TAG, "copyFile " + filename);
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(filename);
      out = new FileOutputStream(newFileName);

      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }

  }
}

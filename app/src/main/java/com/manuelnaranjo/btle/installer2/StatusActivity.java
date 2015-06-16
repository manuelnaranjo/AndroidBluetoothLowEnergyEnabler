package com.manuelnaranjo.btle.installer2;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.CommandCapture;
import com.stericson.RootTools.execution.Shell;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class StatusActivity extends Activity {
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

  // this are assets we always extract
  private static List<String> ASSETS = Arrays.asList(
    "xbin",
    "install.sh",
    "uninstall.sh"
  );

  private String mBOARD;
  private String mBUILD;
  private TextView mTxtDeviceBoard, mTxtApiStatus, mTxtLog;
  private Button mBtnInstall, mBtnUninstall;

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
          resetButtons();
          if (val) {
            reboot();
          } else {
            Toast.makeText(getApplicationContext(),
              "Something failed",
              Toast.LENGTH_LONG).show();
          }
        }


      }
    };
  }

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

    FileOutputStream outputStream = null;
    try {
      outputStream = getApplicationContext().openFileOutput("createme",
        Context.MODE_PRIVATE);
      outputStream.write("Some text".getBytes());
      outputStream.close();
    } catch (Exception e) {
      Log.i(TAG, "failed creating test file");
    }

    mFilesPath = "/data/data/" + this.getPackageName() + "/files/";
    mBusyBox = mFilesPath + "xbin/busybox";

    File temp = new File(mFilesPath);
    if ( temp.exists() && temp.list().length > 0) {
      try {
        recursiveDelete(temp);
        Log.e(TAG, "Cleaned up previous install directory");
      } catch (IOException e) {
        Log.e(TAG, "Failed to do cleanup", e);
      }
    }

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
    mTxtLog = (TextView) this.findViewById(R.id.txtLog);
    mTxtLog.setText("");

    mBtnInstall = (Button) this.findViewById(R.id.btnInstall);
    mBtnUninstall = (Button) this.findViewById(R.id.btnUninstall);

    mBtnInstall.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleInstall();
      }
    });

    mBtnUninstall.setOnClickListener(new OnClickListener() {

      @Override
      public void onClick(View v) {
        handleUninstall();
      }
    });

    mBOARD = android.os.Build.BOARD;
    mBUILD = android.os.Build.ID;

    mTxtDeviceBoard.setText(mBOARD);
    if (!MODELS.contains(mBOARD)) {
      logError("Device is not compatible, Nexus 7 2012, Nexus 10 and Galaxy Nexus only by now");
      mBtnInstall.setEnabled(false);
      mBtnUninstall.setEnabled(false);
      return;
    }

    mCompatible = true;
    testRoot();
  }

  void recursiveDelete(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c: f.listFiles()) {
        recursiveDelete(c);
      }
    }
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f);
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
  }

  public void updateValues() {
    this.runOnUiThread(new Runnable() {

      @Override
      public void run() {
        clearLog();
        mInstalled = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        mTxtApiStatus.setText(mInstalled == true ? R.string.available : R.string.not_installed);
        resetButtons();
      }
    });
  }

  public void resetButtons() {
    this.runOnUiThread(new Runnable() {

      @Override
      public void run() {
        if (mRootReady) {
          mBtnInstall.setEnabled(!mInstalled);
          mBtnUninstall.setEnabled(true);
        } else {
          mBtnInstall.setEnabled(false);
          mBtnUninstall.setEnabled(false);
        }
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_status, menu);
    return true;
  }

  public void clearLog() {
    if (mTxtLog != null)
      this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          mTxtLog.setText("");
        }
      });
  }

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
      try {
        RootTools.restartAndroid();
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
          dir.mkdirs();
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

  public class MyCommandCapture extends CommandCapture {
    public MyCommandCapture(int id, String cmd) {
      super(id, 2*1000*1000, cmd);
    }

    public void commandOutput(int id, String line)
    {
      super.commandOutput(id, line);
      Log.v(TAG, line);
    }

    @Override
    public void commandTerminated(int id, String reason)
    {
      Log.v(TAG,"CommandTerminated " + reason);
    }

    @Override
    public void commandCompleted(int id, int exitcode)
    {
      Log.v(TAG, "CommandCompleted " + exitcode);
    }
  }

  private void handleInstall() {
    disableBluetooth();

    addToLog("Extracting assets");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      copyFileOrDir(mBOARD + "/ble-5.0");
    } else {
      copyFileOrDir(mBOARD + "/ble-4.0");
    }

    addToLog("Starting installation");
    mBtnInstall.setEnabled(false);
    mBtnUninstall.setEnabled(false);

    String command = mBusyBox + " ash " + mFilesPath + "install.sh";
    Log.v(TAG, "Running: " + command);
    Command capture = new MyCommandCapture(10, command);

    try {
      Shell shell = RootTools.getShell(true);
      shell.useCWD(getApplicationContext());
      shell.add(capture);
    } catch (Exception e) {
      logError("Failed to start the installation", e);
    }

  }

  private void disableBluetooth() {

    try {
      BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
      if (mAdapter.isEnabled()) {
        addToLog("Disabling Bluetooth before continuing");
        mAdapter.disable();
        while (mAdapter.isEnabled()) {
          Thread.sleep(10);
        }
      }
    } catch (Exception e) {
      logError("Failed disabling bluetooth", e);
    }

  }

  private void handleUninstall() {
    disableBluetooth();

    addToLog("Extracting assets");

    copyFileOrDir(mBOARD + "/" + mBOARD + "-" + mBUILD.toLowerCase());

    addToLog("Starting removal");
    mBtnInstall.setEnabled(false);
    mBtnUninstall.setEnabled(false);

    String command = mBusyBox + " ash " + mFilesPath + "uninstall.sh";
    Log.v(TAG, "Running: " + command);
    Command capture = new MyCommandCapture(10, command);

    try {
      Shell shell = RootTools.getShell(true);
      shell.useCWD(getApplicationContext());
      shell.add(capture);
    } catch (Exception e) {
      logError("Failed to start the installation", e);
    }

  }
}

package com.manuelnaranjo.btle.installer2;

import com.stericson.RootTools.Command;
import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class RemoveProcess extends Thread {
  private InstallerListener mListener;

  public RemoveProcess(InstallerListener l) {
    mListener = l;
  }

  private boolean sendCommand(String c) {
    try {
      Command cmd = RootTools.getShell(true).add(
        new CommandCapture(0, c));
      return cmd.exitCode() == 0;
    } catch (Exception e) {
      mListener.logError("Failed to execute command " + c, e);
      return false;
    }
  }

  public void run() {
    boolean ret;
    String mBackupPath;
    try {
      mBackupPath = mListener.getBackupDir().getCanonicalPath();
    } catch (IOException e) {
      mListener.logError("Failed to get backup-dir", e);
      return;
    }

    RootTools.debugMode = true;
    RootTools.remount("/system", "RW");

    for (String t : Arrays.asList(
      "/system/vendor/lib/libbt-vendor.so",
      "/system/lib/hw/bluetooth.default.so"
    )) {
      String b = new File(t).getName();
      ret = RootTools.copyFile(mBackupPath + "/" + b, t, false, true);
      if (ret) {
        mListener.logInfo("Restored: " + b);
        sendCommand("rm -f " + mBackupPath + "/" + b);
      } else {
        mListener.logError("Failed to restore: " + b);
      }
    }
    ret = sendCommand(
      "rm -f /system/etc/permissions/android.hardware.bluetooth_le.xml");
    mListener.logInfo("Removed permission file " + (ret
      ? "correctly"
      : "failure"));
    RootTools.remount("/system", "RO");
    mListener.logInfo("Restore complete");
    mListener.logInfo("It's better if you restart your device now");
  }
}

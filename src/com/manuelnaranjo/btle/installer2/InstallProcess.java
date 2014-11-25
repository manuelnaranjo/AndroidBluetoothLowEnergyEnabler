
package com.manuelnaranjo.btle.installer2;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import android.content.Context;

import com.stericson.RootTools.Command;
import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;

public class InstallProcess extends Thread {
  private InstallerListener mListener;
  private String mPath = null;

  public InstallProcess(InstallerListener l) {
    mListener = l;
  }

  private boolean sendCommand(String c){
    try {
      Command cmd = RootTools.getShell(true).add(
        new CommandCapture(0, c));
      return cmd.exitCode() == 0;
    } catch (Exception e) {
      mListener.logError("Failed to execute command " + c, e);
      return false;
    }
  }

  private boolean DumpFile(InputStream inp, File target) {
    int BUFF_SIZE = 1024;
    byte[] buffer = new byte[BUFF_SIZE];
    int count;
    OutputStream out = null;

    target.getParentFile().mkdirs();
    try {
      out = new BufferedOutputStream(new FileOutputStream(target));
      while (inp.available()>0){
        count = inp.read(buffer, 0, BUFF_SIZE);
        out.write(buffer, 0, count);
      }
    } catch (IOException e){
      mListener.logError("Failed while dumping file", e);
    }
    finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException e) {
          return false;
        }
      }
    }

    return true;
  }

  public void run() {
    Context c;
    String mBackupPath;
    boolean ret;

    RootTools.debugMode = true;

    c = mListener.getApplicationContext();
    try {
      mPath = c.getFilesDir().getCanonicalPath();
      mBackupPath = mListener.getBackupDir().getCanonicalPath();
    } catch (IOException e) {
      mListener.logError("Failed to get canonical path", e);
      return;
    }

    new File(mBackupPath).mkdirs();

    mListener.logVerbose("Creating backup into " + mBackupPath);

    for (String t: Arrays.asList(
      "/system/lib/hw/bluetooth.default.so",
      "/system/vendor/lib/libbt-vendor.so"
    )){
      String b = new File(t).getName();

      if (new File(mBackupPath+"/"+b).exists())
        // ignore current backup
        continue;

      ret = RootTools.copyFile(t, mBackupPath + "/" + b, false, true);
      if (ret)
        mListener.logInfo("Backed up file: " + b);
      else {
        mListener.logError("Failed to create backup of file: " + b);
        return;
      }
    }

    String targetPath = mPath + "/target";

    ret = sendCommand("mount -o remount,rw /system");

    if (!ret){
      mListener.logError("Failed to remount /system into writable mode");
      return;
    }

    mListener.logInfo("Remounted /system as writable");

    for (String t: Arrays.asList(
      "lib/hw/bluetooth.default.so",
      "vendor/lib/libbt-vendor.so",
      "etc/permissions/android.hardware.bluetooth_le.xml"
    )){
      InputStream s = mListener.getTargetFile(t);
      if (s == null)
        return;
      File f = new File(targetPath, t);


      DumpFile(s, f);
      String fn = new File(t).getName();
      mListener.logInfo("Dumped " + fn);
      String p = targetPath+"/"+t;

      String st = "/system/" + t;

      ret = sendCommand("cp " + p + " "+ st);
      mListener.logInfo("Copied " + fn + " " + (ret ? "correctly" : "failure"));
      if (!ret)
        continue;

      ret = sendCommand("chmod 755 " + st);
      mListener.logInfo("chmod " + fn + " " + (ret ? "correctly" : "failure"));
      if (!ret)
        continue;

      ret = sendCommand("chown root:root " + st);
      mListener.logInfo("chown " + fn + " " + (ret ? "correctly" : "failure"));
      if (!ret)
        continue;
    }

    sendCommand("mount -o remount,ro /system");

    if (ret)
      mListener.logInfo("Everything installed reboot your device now");
    else
      mListener.logInfo("Something failed, request assistance");
  }
}

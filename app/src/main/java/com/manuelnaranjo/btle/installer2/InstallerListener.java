package com.manuelnaranjo.btle.installer2;

import java.io.File;
import java.io.InputStream;

import android.content.Context;

interface InstallerListener {
  public void clearLog();
  public void logInfo(String t);
  public void logVerbose(String t);
  public void logError(String t);
  public void logError(String t, Exception e);
  public void addToLog(String t);
  public void reboot();
  public Context getApplicationContext();
  public void updateValues();
  public File getBackupDir();
  public InputStream getTargetFile(String path);
}

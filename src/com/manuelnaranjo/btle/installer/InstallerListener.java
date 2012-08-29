package com.manuelnaranjo.btle.installer;

import android.content.Context;

interface InstallerListener {
    public void clearLog();
    public void addToLog(String t);
    public void reboot();
    public Context getApplicationContext();
}
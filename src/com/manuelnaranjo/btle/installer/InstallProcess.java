
package com.manuelnaranjo.btle.installer;

import android.content.Context;
import android.util.Log;

import com.stericson.RootTools.Command;
import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.Shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class InstallProcess extends Thread {
    private InstallerListener mListener;
    private String mPath = null;
    
    static final String FRAMEWORK_PATH="/system/framework/btle-framework.jar";
    static final String PERM_PATH="/system/etc/permissions/com.manuelnaranjo.broadcom.bt.le.xml";
    private static final String SH_HEAD="#!/system/bin/sh";

    public InstallProcess(InstallerListener l) {
        mListener = l;
        mListener.clearLog();
    }
    
    private void cleanup(){
        if (mPath!=null){
            CommandCapture cmd = new CommandCapture(0, "rm -rf " + mPath + File.separator + "*");
            try {
                RootTools.getShell(true).add(cmd).waitForFinish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        RootTools.remount("/system", "RO");
        
    }
    
    public boolean chmod(String path, String perm){
        try{
            Command cmd = RootTools.getShell(true).add(
                    new CommandCapture(0, "chmod " + perm +" " + path));
            if (cmd.exitCode()!= 0)
                throw new RuntimeException("file: " + path + ", exit code: " + cmd.exitCode());
            return true;
        } catch (Exception e){
            this.mListener.addToLog("Error while doing chmod: " + e.getMessage());
            Log.e(StatusActivity.TAG, "error on chmod", e);
        }
        return false;
    }
    
    public String getFileHeader(String path, int max_length){
        String out = null;
        
        File f = new File(path);
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            if (max_length < 0){
                return br.readLine();
            }
            char[] t = new char[max_length];
            int l = br.read(t, 0, max_length);
            out = new String(t, 0, l);
        } catch (IOException e){
            this.mListener.addToLog("Failed to read " + path);
            Log.e(StatusActivity.TAG, "failed to read", e);
        }
        
        return out;
    }
    
    public boolean processDbusDaemon(Context c){
        if (!RootTools.copyFile("/system/bin/dbus-daemon", "/system/bin/dbus-daemon.orig", true, true)){
            mListener.addToLog("Failed to copy dbus-daemon");
            return false;
        }
        mListener.addToLog("copied dbus-daemon");
        
        boolean ret;
        ret = RootTools.installBinary(c, R.raw.dbus_daemon, "dbus-daemon");
        if (!ret){
            mListener.addToLog("failed to extract dbus-daemon replacement");
            return false;
        }
        
        String ipath = mPath + File.separator + "dbus-daemon";
        mListener.addToLog("extracted dbus-daemon wrapper");
        
        Shell s = null;
        try {
            s = RootTools.getShell(true);
        } catch (Exception e){
            mListener.addToLog("Failed to get a new rooted shell");
            return false;
        }
        mListener.addToLog("got rooted shell");
        
        CommandCapture cmd;
        cmd = new CommandCapture(0, "ls -n /system/bin/dbus-daemon");
        try {
            if (s.add(cmd).exitCode()!=0){
                mListener.addToLog("Failed to get dbus-daemon owner");
                return false;
            }
        } catch (Exception e){
            mListener.addToLog("Failed to run ls on dbus-daemon");
            return false;
        }
        mListener.addToLog("got dbus-daemon owner");
        
        try {
            if (s.add(new CommandCapture(0, "chmod 0755 " + ipath )).exitCode()!=0){
                mListener.addToLog("Failed to set wrapper permissions");
                return false;
            }
        } catch (Exception e) {
            Log.e(StatusActivity.TAG, "faield to do chmod", e);
            mListener.addToLog("Exception during chmod");
            return false;
        }
        mListener.addToLog("chmod of wrapper succesful");
        
        String[] t = cmd.toString().split("[ ]+");
        String oid = t[1];
        String gid = t[2];
        
        try {
            if (s.add(new CommandCapture(0, "chown " + oid + ":" + gid + " " + ipath )).exitCode()!=0){
                mListener.addToLog("Failed to set wrapper owner");
                return false;
            }
        } catch (Exception e) {
            Log.e(StatusActivity.TAG, "faield to do chown", e);
            mListener.addToLog("Exception during chown");
            return false;
        }
        mListener.addToLog("chown of wrapper succesful");
        
        ret = RootTools.copyFile(ipath, "/system/bin/dbus-daemon", true, true);
        if (!ret){
            mListener.addToLog("Failed to overwrite dbus-daemon with wrapper");
            return false;
        }
                
        mListener.addToLog("Wrapper installed");
        return true;
    }
    
    public boolean installBinary(int src, String resname, String target, String perm){
        boolean ret;
        Context c;
        c=mListener.getApplicationContext();
        
        ret = RootTools.installBinary(c, src, resname);
        if (!ret){
            mListener.addToLog("Failed to extract resource " + resname);
            return false;
        }
        
        mListener.addToLog("Copied resource to " + resname);
        
        String ipath;
        ipath = mPath+File.separator+resname;
        if (!chmod(ipath, perm)) {
            return false;
        }
        
        ret = RootTools.copyFile(ipath, target, true, false);
        if (!ret){
            mListener.addToLog("Failed to copy framework into " + target);
            return false;
        }
        
        return true;

    }

    public void run() {
        boolean ret;
        Context c;
        String fname, ipath;
        
        RootTools.debugMode = true;
        
        c = mListener.getApplicationContext();
        try {
            mPath = c.getFilesDir().getCanonicalPath();
        } catch (IOException e) {
           Log.e(StatusActivity.TAG, "failed to get canonical path", e);
           mListener.addToLog("failed to get canonical path");
           return;
        }
        
        fname = "btle-framework-" + c.getString(R.string.current_framework_version) + ".jar";
        
        if (!this.installBinary(R.raw.btle_framework, fname, FRAMEWORK_PATH, "644")){
            cleanup();
            return;
        }
        mListener.addToLog("Installed framework");
        
        fname = "com.manuelnaranjo.android.bluetooth.le.xml";
        if (!this.installBinary(R.raw.android_bluetooth_le, fname, PERM_PATH, "644")){
            cleanup();
            return;
        }
        mListener.addToLog("Installed permission");
        
        String fheader = getFileHeader("/system/bin/dbus-daemon", SH_HEAD.length());
        Log.v(StatusActivity.TAG, "got file header " + fheader);
        if (!SH_HEAD.equals(fheader)){
            if (!processDbusDaemon(c)){
                cleanup();
            }
        }
        
        CommandCapture cmd = new CommandCapture(0, "/system/bin/dbus-daemon --version");
        try {
            RootTools.getShell(true).add(cmd).waitForFinish();
            if (cmd.exitCode()!=0){
                mListener.addToLog("WARN: failed to update dalvik cache");
                cleanup();
                return;
            }
        } catch (Exception e) {
            mListener.addToLog("WARN: failed to update dalvik cache");
            Log.e(StatusActivity.TAG, "failed to update dalvik cache", e);
            cleanup();
            return;
        } 
        
        mListener.addToLog("Installation done");
        mListener.addToLog("It's better if you restart your cellphone");
        mListener.reboot();
        cleanup();
    }
}

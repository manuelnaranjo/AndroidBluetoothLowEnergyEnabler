
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
    private static final String TAG = StatusActivity.TAG;
    
    static final String WRAPPER_NAME="netd";
    static final String WRAPPER_PATH="/system/bin/netd";
    static final String FRAMEWORK_PATH="/system/framework/btle-framework.jar";
    static final String PERM_PATH="/system/etc/permissions/com.manuelnaranjo.broadcom.bt.le.xml";
    static final String LAUNCH_PATH="/system/bin/btle-framework";
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
    
    private boolean chmod(String path, String perm){
        try{
            RootTools.remount("/system", "RW");
            Command cmd = RootTools.getShell(true).add(
                    new CommandCapture(0, "chmod " + perm + " " + path));
            RootTools.remount("/system", "RO");
            if (cmd.exitCode()!= 0)
                throw new RuntimeException("file: " + path + 
                        ", exit code: " + cmd.exitCode());
            return true;
        } catch (Exception e){
            this.mListener.addToLog("Error while doing chmod: " + 
                        e.getMessage());
            Log.e(TAG, "error on chmod", e);
        }
        return false;
    }
    
    private boolean chown(String path, String perm){
        try{
            RootTools.remount(path, "RW");
            Command cmd = RootTools.getShell(true).add(
                    new CommandCapture(0, "chown " + perm +" " + path));
            if (cmd.exitCode()!= 0)
                throw new RuntimeException("file: " + path + 
                        ", exit code: " + cmd.exitCode());
            return true;
        } catch (Exception e){
            this.mListener.addToLog("Error while doing chown: " + 
                    e.getMessage());
            Log.e(TAG, "error on chown", e);
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
            Log.e(TAG, "failed to read", e);
        }
        
        return out;
    }
    
    public boolean processWrapper(Context c){
        if (!RootTools.copyFile(WRAPPER_PATH, 
                WRAPPER_PATH + ".orig", true, true)){
            mListener.addToLog("Failed to copy wrapper exe");
            return false;
        }
        mListener.addToLog("backed up wrapper");
        
        boolean ret;
        ret = RootTools.installBinary(c, R.raw.wrapper, WRAPPER_NAME, "0755");
        if (!ret){
            mListener.addToLog("failed to extract wrapper replacement");
            return false;
        }
        
        String ipath = mPath + File.separator + WRAPPER_NAME;
        mListener.addToLog("extracted wrapper");
        
        Shell s = null;
        try {
            s = RootTools.getShell(true);
        } catch (Exception e){
            mListener.addToLog("Failed to get a new rooted shell");
            return false;
        }
        mListener.addToLog("got rooted shell");
        
        CommandCapture cmd;
        cmd = new CommandCapture(0, "ls -n " + WRAPPER_PATH);
        try {
            if (s.add(cmd).exitCode()!=0){
                mListener.addToLog("Failed to get wrapper owner");
                return false;
            }
        } catch (Exception e){
            mListener.addToLog("Failed to run ls on wrapper");
            return false;
        }
        mListener.addToLog("got wrapper owner");
        
        String[] t = cmd.toString().split("[ ]+");
        String oid = t[1];
        String gid = t[2];
        
        if (!chown(ipath, oid+":"+gid)){
            mListener.addToLog("Failed to change wrapper owner");
            return false;
        }
        
        mListener.addToLog("chown of wrapper succesful");
        
        ret = RootTools.copyFile(ipath, WRAPPER_PATH, true, true);
        if (!ret){
            mListener.addToLog("Failed to overwriter original with wrapper");
            return false;
        }
                
        mListener.addToLog("Wrapper installed");
        return true;
    }
    
    public boolean installBinary(int src, String resname, String target, String perm){
        boolean ret;
        Context c;
        c=mListener.getApplicationContext();
        
        ret = RootTools.installBinary(c, src, resname, perm);
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
        
        ret = RootTools.copyFile(ipath, target, true, true);
        if (!ret){
            mListener.addToLog("Failed to copy framework into " + target);
            return false;
        }
        
        return true;

    }

    public void run() {
        Context c;
        String fname;
        
        RootTools.debugMode = true;
        
        c = mListener.getApplicationContext();
        try {
            mPath = c.getFilesDir().getCanonicalPath();
        } catch (IOException e) {
           Log.e(TAG, "failed to get canonical path", e);
           mListener.addToLog("failed to get canonical path");
           return;
        }
        
        fname = "btle-framework-" + c.getString(R.string.current_framework_version) + ".jar";
        
        if (!this.installBinary(R.raw.btle_framework, fname, FRAMEWORK_PATH, "0644")){
            cleanup();
            return;
        }
        mListener.addToLog("Installed framework");
        
        fname = "com.manuelnaranjo.android.bluetooth.le.xml";
        if (!this.installBinary(R.raw.android_bluetooth_le, fname, PERM_PATH, "0644")){
            cleanup();
            return;
        }
        mListener.addToLog("Installed permission");
        
        fname = "btle-framework";
        if (!this.installBinary(R.raw.btle_framework_script, fname, LAUNCH_PATH, "0755")){
            cleanup();
            return;
        }
        mListener.addToLog("Installed btle-framework launcher");
        
        String fheader = getFileHeader(WRAPPER_PATH, SH_HEAD.length());
        Log.v(TAG, "got file header " + fheader);
        if (!SH_HEAD.equals(fheader)){
            if (!processWrapper(c)){
                cleanup();
            }
        }
        
        CommandCapture cmd = new CommandCapture(0, "/system/bin/btle-framework --version");
        try {
            RootTools.getShell(true).add(cmd).waitForFinish();
            if (cmd.exitCode()!=0){
                mListener.addToLog("WARN: failed to update dalvik cache");
                cleanup();
                return;
            }
        } catch (Exception e) {
            mListener.addToLog("WARN: failed to update dalvik cache");
            Log.e(TAG, "failed to update dalvik cache", e);
            cleanup();
            return;
        } 
        
        mListener.addToLog("Installation done");
        mListener.addToLog("It's better if you restart your cellphone");
        mListener.reboot();
        cleanup();
    }
}

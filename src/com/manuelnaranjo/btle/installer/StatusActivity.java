
package com.manuelnaranjo.btle.installer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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

import java.io.IOException;
import java.lang.reflect.Method;

public class StatusActivity extends Activity implements InstallerListener {
    static final String TAG = "BTLE-Status";

    private TextView mTxtInstalledAPIVersion, mTxtProvidedAPIVersion,
            mTxtSystemReady, mTxtInstalledFrameworkVersion,
            mTxtProvidedFrameworkVersion, mTxtLog;
    private Button mBtnInstall, mBtnUninstall;

    private boolean mRootReady = false;
    private boolean mInstalled = false;

    public String testRoot() {
        if (!RootTools.isRootAvailable()) {
            mTxtLog.append("root not available\n");
            Log.e(TAG, "root not available");
            return getResources().getString(R.string.root_not_available);
        }

        if (!RootTools.isAccessGiven()) {
            Log.e(TAG, "root not given");
            mTxtLog.append("root not granted\n");
            return getResources().getString(R.string.root_required);
        }

        mTxtLog.append("root ready\n");
        mRootReady = true;
        return getResources().getString(R.string.system_ok);
    }

    public String getInstalledAPIVersionNumber() {
        Log.i(TAG, "getting installed api version number");

        Class<?> bleAdapter;
        try {
            bleAdapter = Class.forName("com.broadcom.bt.le.api.BleAdapter");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "didn't find bleadapter", e);
            mTxtLog.append("bleadapter not provided\n");
            return getResources().getString(R.string.not_installed);
        }

        Log.i(TAG, "got library");

        Method apiLevelMethod;

        try {
            apiLevelMethod = bleAdapter.getMethod("getApiLevel", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "no api level", e);
            mTxtLog.append("no api level\n");
            return getResources().getString(R.string.no_api_level);
        }

        Log.i(TAG, "got method");

        Object apiLevelObject;

        try {
            apiLevelObject = apiLevelMethod.invoke(null, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, "failed to get API level", e);
            mTxtLog.append("failed to get API level\n");
            return getResources().getString(R.string.api_level_invocation);
        }

        Log.i(TAG, "invoked method: " + apiLevelObject);

        int apiLevel = ((Integer) apiLevelObject).intValue();

        Log.i(TAG, "installed api level: " + apiLevel);
        mTxtLog.append("found api level: " + apiLevel + "\n");

        mInstalled = true;

        return Integer.toString(apiLevel);
    }
    
    public String getInstalledFrameworkVersionNumber() {
        Log.i(TAG, "getting installed framework version number");

        Class<?> bleAdapter;
        try {
            bleAdapter = Class.forName("com.broadcom.bt.le.api.BleAdapter");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "didn't find bleadapter", e);
            mTxtLog.append("bleadapter not provided\n");
            return getResources().getString(R.string.not_installed);
        }

        Log.i(TAG, "got library");

        Method frameworkVersionMethod;

        try {
            frameworkVersionMethod = bleAdapter.getMethod("getFrameworkVersion", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "no framework version", e);
            mTxtLog.append("no framework version\n");
            return getResources().getString(R.string.no_framework_version);
        }

        Log.i(TAG, "got method");

        Object frameworkVersionObject;

        try {
            frameworkVersionObject = frameworkVersionMethod.invoke(null, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, "failed to get framework version", e);
            mTxtLog.append("failed to get framework version\n");
            return getResources().getString(R.string.framework_version_invocation);
        }

        Log.i(TAG, "invoked method: " + frameworkVersionObject);

        return (String)frameworkVersionObject;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        mTxtInstalledAPIVersion = (TextView) this.findViewById(R.id.txtInstalledAPIVer);
        mTxtProvidedAPIVersion = (TextView) this.findViewById(R.id.txtProvidedAPIVer);
        mTxtInstalledFrameworkVersion = (TextView) this.findViewById(R.id.txtInstalledFrameworkVer);
        mTxtProvidedFrameworkVersion = (TextView) this.findViewById(R.id.txtProvidedFrameworkVer);
        mTxtSystemReady = (TextView) this.findViewById(R.id.txtSystemReady);
        mTxtLog = (TextView) this.findViewById(R.id.txtLog);
        mTxtLog.setText("");

        mBtnInstall = (Button) this.findViewById(R.id.btnInstall);
        mBtnUninstall = (Button) this.findViewById(R.id.btnUninstall);

        String apiLevel = getResources().getString(R.string.current_api_version);
        mTxtProvidedAPIVersion.setText(apiLevel);
        
        String frameworkLevel = getResources().getString(R.string.current_framework_version);
        mTxtProvidedFrameworkVersion.setText(frameworkLevel);
        
        mBtnInstall.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                InstallProcess p = new InstallProcess(StatusActivity.this);
                addToLog("Starting installation");
                p.start();
            }

        });
        
        mBtnUninstall.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                RemoveProcess p = new RemoveProcess(StatusActivity.this);
                addToLog("Removing installation");
                p.start();
            }

        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mTxtInstalledAPIVersion.setText(getInstalledAPIVersionNumber());
        mTxtInstalledFrameworkVersion.setText(getInstalledFrameworkVersionNumber());
        mTxtSystemReady.setText(testRoot());

        mBtnInstall.setEnabled(mRootReady);
        mBtnUninstall.setEnabled(mRootReady && mInstalled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_status, menu);
        return true;
    }
    
    @Override
    public void clearLog(){
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mTxtLog.setText("");
            }

        });
        
    }

    @Override
    public void addToLog(final String t) {
        this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Log.i(TAG, t);
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
    
    yesNoListener rebootListener = new yesNoListener () {
        public void actionYes(){
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
        
        public void actionNo(){
            Toast.makeText(getApplicationContext(), 
                    "Reboot needed before you can use this activity again",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    };
    
    @Override
    public void reboot(){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                yesNoAlert("Reboot device?", 
                        "You need to reboot your device to complete this action", 
                        rebootListener);
            }
        });
    }
}

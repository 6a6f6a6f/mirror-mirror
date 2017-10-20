package com.mparticle.mock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.telephony.TelephonyManager;

import org.mockito.Mockito;

import static junit.framework.Assert.fail;

public class MockContext extends android.test.mock.MockContext {

    SharedPreferences sharedPreferences = new MockSharedPreferences();
    Resources resources = new MockResources();
    MockApplication application = null;

    @Override
    public Context getApplicationContext() {
        if (application == null){
            application = new MockApplication(this);
        }
        return application;
    }

    public void setSharedPreferences(SharedPreferences prefs){
        sharedPreferences = prefs;
    }

    @Override
    public void sendBroadcast(Intent intent) {
        
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return sharedPreferences;
    }

    @Override
    public Object getSystemService(String name) {
        if (name.equals(Context.TELEPHONY_SERVICE)){
            return Mockito.mock(TelephonyManager.class);
        }
        return super.getSystemService(name);
    }

    @Override
    public PackageManager getPackageManager() {
        PackageManager manager = Mockito.mock(PackageManager.class);
        PackageInfo info = Mockito.mock(PackageInfo.class);
        info.versionName = "42";
        info.versionCode = 42;
        ApplicationInfo appInfo = Mockito.mock(ApplicationInfo.class);
        try {
            Mockito.when(manager.getPackageInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(info);
            Mockito.when(manager.getInstallerPackageName(Mockito.anyString())).thenReturn("com.mparticle.test.installer");

            Mockito.when(manager.getApplicationInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(appInfo);
            Mockito.when(manager.getApplicationLabel(appInfo)).thenReturn("test label");
        }catch (Exception e){
            fail(e.toString());
        }
        return manager;
    }

    @Override
    public String getPackageName() {
        return "com.mparticle.test";
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return new ApplicationInfo();
    }

    @Override
    public Resources getResources() {
        return resources;
    }
}

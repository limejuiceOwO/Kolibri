package com.filetransfer.kolibri;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import com.filetransfer.kolibri.db.MainDatabase;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class DbHelper {
    @Test
    public void cleanDatabase() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.filetransfer.kolibri", appContext.getPackageName());
        MainDatabase db = MainDatabase.getInstance(appContext);
        db.chatDao().removeAll();
        db.fileDao().removeAll();
    }
}
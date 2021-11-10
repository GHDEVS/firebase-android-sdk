// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Class responsible for storing all heartbeat related information.
 *
 * <p>This exposes functions to check if there is a need to send global/sdk heartbeat.
 */
public class HeartBeatInfoStorage {
  private static HeartBeatInfoStorage instance = null;

  private static final String GLOBAL = "fire-global";

  private static final String PREFERENCES_NAME = "FirebaseAppHeartBeat";

  private static final String HEARTBEAT_PREFERENCES_NAME = "FirebaseHeartBeat";

  private static final String HEART_BEAT_COUNT_TAG = "fire-count";

  private static final String LAST_STORED_DATE = "last-used-date";

  // As soon as you hit the limit of heartbeats. The number of stored heartbeats is halved.
  private static final int HEART_BEAT_COUNT_LIMIT = 30;

  private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("dd/MM/yyyy z");

  private final SharedPreferences sharedPreferences;
  private final SharedPreferences firebaseSharedPreferences;

  public HeartBeatInfoStorage(Context applicationContext, String persistenceKey) {
    this.sharedPreferences =
        directBootSafe(applicationContext)
            .getSharedPreferences(PREFERENCES_NAME + persistenceKey, Context.MODE_PRIVATE);
    this.firebaseSharedPreferences =
        directBootSafe(applicationContext)
            .getSharedPreferences(
                HEARTBEAT_PREFERENCES_NAME + persistenceKey, Context.MODE_PRIVATE);
  }

  private static Context directBootSafe(Context applicationContext) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      return applicationContext;
    }
    return ContextCompat.createDeviceProtectedStorageContext(applicationContext);
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  HeartBeatInfoStorage(SharedPreferences preferences, SharedPreferences firebaseSharedPreferences) {
    this.sharedPreferences = preferences;
    this.firebaseSharedPreferences = firebaseSharedPreferences;
  }

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  int getHeartBeatCount() {
    return (int) this.firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
  }

  synchronized void deleteAllHeartBeats() {
    firebaseSharedPreferences.edit().clear().apply();
  }

  synchronized List<HeartBeatResult> getAllHeartBeats() {
    ArrayList<HeartBeatResult> heartBeatResults = new ArrayList<>();
    for (Map.Entry<String, ?> entry : this.firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        heartBeatResults.add(
            HeartBeatResult.create(
                entry.getKey(), new ArrayList<String>((HashSet<String>) entry.getValue())));
      }
    }
    return heartBeatResults;
  }

  synchronized void storeHeartBeat(long millis, String userAgentString) {
    long heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    if (heartBeatCount + 1 == HEART_BEAT_COUNT_LIMIT) {
      cleanUpStoredHeartBeats();
      heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    }
    String dateString = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    String lastDateString = firebaseSharedPreferences.getString(LAST_STORED_DATE, "");
    if (!lastDateString.equals(dateString)) {
      Set<String> userAgentDateSet =
          new HashSet<String>(
              firebaseSharedPreferences.getStringSet(userAgentString, new HashSet<String>()));
      userAgentDateSet.add(dateString);
      heartBeatCount += 1;
      firebaseSharedPreferences
          .edit()
          .putStringSet(userAgentString, userAgentDateSet)
          .putLong(HEART_BEAT_COUNT_TAG, heartBeatCount)
          .putString(LAST_STORED_DATE, dateString)
          .apply();
    }
  }

  private synchronized void cleanUpStoredHeartBeats() {
    long heartBeatCount = firebaseSharedPreferences.getLong(HEART_BEAT_COUNT_TAG, 0);
    String lowestDate = null;
    String userAgentString = "";
    for (Map.Entry<String, ?> entry : firebaseSharedPreferences.getAll().entrySet()) {
      if (entry.getValue() instanceof Set) {
        HashSet<String> dateSet = (HashSet<String>) entry.getValue();
        for (String ele : dateSet) {
          if (lowestDate == null || lowestDate.compareTo(ele) > 0) {
            lowestDate = ele;
            userAgentString = entry.getKey();
          }
        }
      }
    }
    Set<String> userAgentDateSet =
        new HashSet<String>(
            firebaseSharedPreferences.getStringSet(userAgentString, new HashSet<String>()));
    userAgentDateSet.remove(lowestDate);
    firebaseSharedPreferences
        .edit()
        .putStringSet(userAgentString, userAgentDateSet)
        .putLong(HEART_BEAT_COUNT_TAG, heartBeatCount - 1)
        .apply();
  }

  synchronized long getLastGlobalHeartBeat() {
    return sharedPreferences.getLong(GLOBAL, -1);
  }

  synchronized void updateGlobalHeartBeat(long millis) {
    sharedPreferences.edit().putLong(GLOBAL, millis).apply();
  }

  static boolean isSameDateUtc(long base, long target) {
    Date baseDate = new Date(base);
    Date targetDate = new Date(target);
    return !(FORMATTER.format(baseDate).equals(FORMATTER.format(targetDate)));
  }

  /*
   Indicates whether or not we have to send a sdk heartbeat.
   A sdk heartbeat is sent either when there is no heartbeat sent ever for the sdk or
   when the last heartbeat send for the sdk was later than a day before.
  */
  synchronized boolean shouldSendSdkHeartBeat(String heartBeatTag, long millis) {
    if (sharedPreferences.contains(heartBeatTag)) {
      if (isSameDateUtc(sharedPreferences.getLong(heartBeatTag, -1), millis)) {
        sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
        return true;
      }
      return false;
    } else {
      sharedPreferences.edit().putLong(heartBeatTag, millis).apply();
      return true;
    }
  }

  /*
   Indicates whether or not we have to send a global heartbeat.
   A global heartbeat is set only once per day.
  */
  synchronized boolean shouldSendGlobalHeartBeat(long millis) {
    return shouldSendSdkHeartBeat(GLOBAL, millis);
  }
}

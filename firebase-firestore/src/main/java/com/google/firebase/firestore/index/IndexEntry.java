// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.index;

/**
 * Represents an index entry saved by the SDK in the local storage. Temporary placeholder, since
 * we'll probably serialize the indexValue right away rather than store it.
 */
// TODO(indexing)
public class IndexEntry {
  private final int indexId;
  private final byte[] arrayValue;
  private final byte[] directionalValue;
  private final String uid;
  private final String documentName;

  public IndexEntry(
      int indexId, byte[] arrayValue, byte[] directionalValue, String uid, String documentName) {
    this.indexId = indexId;
    this.arrayValue = arrayValue;
    this.directionalValue = directionalValue;
    this.uid = uid;
    this.documentName = documentName;
  }

  public int getIndexId() {
    return indexId;
  }

  public byte[] getArrayValue() {
    return arrayValue;
  }

  public byte[] getDirectionalValue() {
    return directionalValue;
  }

  public String getUid() {
    return uid;
  }

  public String getDocumentName() {
    return documentName;
  }
}

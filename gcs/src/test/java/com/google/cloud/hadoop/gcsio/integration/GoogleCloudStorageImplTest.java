/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.hadoop.gcsio.integration;


import static org.junit.Assert.fail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.hadoop.gcsio.CreateObjectOptions;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageImpl;
import com.google.cloud.hadoop.gcsio.GoogleCloudStorageOptions;
import com.google.cloud.hadoop.gcsio.StorageResourceId;
import com.google.cloud.hadoop.gcsio.integration.GoogleCloudStorageTestHelper.TestBucketHelper;
import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Tests that require a particular configuration of GoogleCloudStorageImpl.
 */
@RunWith(JUnit4.class)
public class GoogleCloudStorageImplTest {

  TestBucketHelper bucketHelper = new TestBucketHelper("gcs_impl");
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  protected GoogleCloudStorageImpl makeStorageWithBufferSize(int bufferSize) throws IOException {
    GoogleCloudStorageOptions.Builder builder =
        GoogleCloudStorageTestHelper.getStandardOptionBuilder();

    builder.getWriteChannelOptionsBuilder()
        .setUploadBufferSize(bufferSize);

    Credential credential = GoogleCloudStorageTestHelper.getCredential();

    return new GoogleCloudStorageImpl(builder.build(), credential);
  }

  protected GoogleCloudStorageImpl makeStorageWithMarkerFileCreation(
      boolean createMarkerFiles) throws IOException {
    GoogleCloudStorageOptions.Builder builder =
        GoogleCloudStorageTestHelper.getStandardOptionBuilder();

    builder.setCreateMarkerObjects(createMarkerFiles);

    Credential credential = GoogleCloudStorageTestHelper.getCredential();

    return new GoogleCloudStorageImpl(builder.build(), credential);
  }

  @Test
  public void testReadAndWriteLargeObjectWithSmallBuffer() throws IOException {
    String bucketName = bucketHelper.getUniqueBucketName("write_large_obj");
    StorageResourceId resourceId = new StorageResourceId(bucketName, "LargeObject");

    GoogleCloudStorageImpl gcs = makeStorageWithBufferSize(1 * 1024 * 1024);

    try {
      gcs.create(bucketName);
      GoogleCloudStorageTestHelper.readAndWriteLargeObject(resourceId, gcs);

    } finally {
      GoogleCloudStorageTestHelper.cleanupTestObjects(
          gcs,
          ImmutableList.of(bucketName),
          ImmutableList.of(resourceId));
    }
  }

  @Test
  public void testNonAlignedWriteChannelBufferSize() throws IOException {
    String bucketName = bucketHelper.getUniqueBucketName("write_3m_buff_obj");
    StorageResourceId resourceId = new StorageResourceId(bucketName, "LargeObject");

    GoogleCloudStorageImpl gcs = makeStorageWithBufferSize(3 * 1024 * 1024);

    try {
      gcs.create(bucketName);
      GoogleCloudStorageTestHelper.readAndWriteLargeObject(resourceId, gcs);

    } finally {
      GoogleCloudStorageTestHelper.cleanupTestObjects(
          gcs,
          ImmutableList.of(bucketName),
          ImmutableList.of(resourceId));
    }
  }

  @Test
  public void testConflictingWritesWithMarkerFiles() throws IOException {
    String bucketName = bucketHelper.getUniqueBucketName("with_marker");
    StorageResourceId resourceId = new StorageResourceId(bucketName, "obj1");

    GoogleCloudStorageImpl gcs = makeStorageWithMarkerFileCreation(true);

    try {
      gcs.create(bucketName);
      byte[] bytesToWrite = new byte[1024];
      GoogleCloudStorageTestHelper.fillBytes(bytesToWrite);
      WritableByteChannel byteChannel1 = gcs.create(resourceId, new CreateObjectOptions(false));
      byteChannel1.write(ByteBuffer.wrap(bytesToWrite));

      // This call should fail:
      expectedException.expectMessage("already exists");
      WritableByteChannel byteChannel2 = gcs.create(resourceId, new CreateObjectOptions(false));
      fail("Creating the second byte channel should fail.");
    } finally {
      GoogleCloudStorageTestHelper.cleanupTestObjects(
          gcs,
          ImmutableList.of(bucketName),
          ImmutableList.of(resourceId));
    }
  }

  @Test
  public void testConflictingWritesWithoutMarkerFiles() throws IOException {
    String bucketName = bucketHelper.getUniqueBucketName("without_marker");
    StorageResourceId resourceId = new StorageResourceId(bucketName, "obj1");

    GoogleCloudStorageImpl gcs = makeStorageWithMarkerFileCreation(false);

    try {
      gcs.create(bucketName);
      byte[] bytesToWrite = new byte[1024];
      GoogleCloudStorageTestHelper.fillBytes(bytesToWrite);
      WritableByteChannel byteChannel1 = gcs.create(resourceId, new CreateObjectOptions(false));
      byteChannel1.write(ByteBuffer.wrap(bytesToWrite));

      // Creating this channel should succeed. Only when we close will an error bubble up.
      WritableByteChannel byteChannel2 = gcs.create(resourceId, new CreateObjectOptions(false));

      byteChannel1.close();

      // Closing byte channel2 should fail:
      expectedException.expectMessage("412 Precondition Failed");
      byteChannel2.close();
      fail("Closing the second byte channel should fail.");
    } finally {
      GoogleCloudStorageTestHelper.cleanupTestObjects(
          gcs,
          ImmutableList.of(bucketName),
          ImmutableList.of(resourceId));
    }
  }
}

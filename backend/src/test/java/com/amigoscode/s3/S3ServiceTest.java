package com.amigoscode.s3;

import com.amigoscode.customer.Customer;
import com.amigoscode.customer.Gender;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {
    @Mock
    private S3Client s3Client;
    private S3Service underTest;

    @BeforeEach
    void setUp() {
        underTest = new S3Service(s3Client);
    }

    @Test
    void canPutObject() throws IOException {
        // Given
        String bucket = "customer";
        String key = "foo";
        byte[] data = "Hello World".getBytes();

        // When
        underTest.putObject(bucket, key, data);

        // Then
        ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyArgumentCaptor =
                ArgumentCaptor.forClass(RequestBody.class);
        Mockito.verify(s3Client).putObject(
                putObjectRequestArgumentCaptor.capture(),
                requestBodyArgumentCaptor.capture()
        );

        PutObjectRequest putObjectRequestArgumentCaptorValue =
                putObjectRequestArgumentCaptor.getValue();

        assertThat(putObjectRequestArgumentCaptorValue.bucket()).isEqualTo(bucket);
        assertThat(putObjectRequestArgumentCaptorValue.key()).isEqualTo(key);

        RequestBody requestBodyArgumentCaptorValue = requestBodyArgumentCaptor.getValue();

        assertThat(
                requestBodyArgumentCaptorValue.contentStreamProvider().newStream().readAllBytes()
        ).isEqualTo(
                RequestBody.fromBytes(data).contentStreamProvider().newStream().readAllBytes()
        );
    }

    @Test
    void canGetObject() throws IOException {
        // Given
        String bucket = "customer";
        String key = "foo";
        byte[] data = "Hello World".getBytes();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> res
                = Mockito.mock(ResponseInputStream.class);
        Mockito.when(res.readAllBytes()).thenReturn(data);

        Mockito.when(s3Client.getObject(eq(getObjectRequest))).thenReturn(res);

        // When
        byte[] bytes = underTest.getObject(bucket, key);

        // Then
        assertThat(bytes).isEqualTo(data);
    }

    @Test
    void willThrowWhenGetObject() throws IOException {
        // Given
        String bucket = "customer";
        String key = "foo";
        byte[] data = "Hello World".getBytes();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> res
                = Mockito.mock(ResponseInputStream.class);
        Mockito.when(res.readAllBytes())
                .thenThrow(new IOException("Cannot read bytes"));

        Mockito.when(s3Client.getObject(eq(getObjectRequest)))
                .thenReturn(res);

        // When
        // Then
        assertThatThrownBy(() -> underTest.getObject(bucket, key))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot read bytes")
                .hasRootCauseInstanceOf(IOException.class);
    }

}
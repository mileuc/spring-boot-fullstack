package com.amigoscode.customer;

import com.amigoscode.exception.DuplicateResourceException;
import com.amigoscode.exception.RequestValidationException;
import com.amigoscode.exception.ResourceNotFoundException;
import com.amigoscode.s3.S3Buckets;
import com.amigoscode.s3.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.ImagingOpException;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;

class CustomerServiceTest {

    @Mock
    private CustomerDao customerDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private S3Service s3Service;
    @Mock
    private S3Buckets s3Buckets;
    private CustomerService underTest;
    private AutoCloseable autoCloseable;

    private final CustomerDTOMapper customerDTOMapper = new CustomerDTOMapper();

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        underTest = new CustomerService(customerDao, customerDTOMapper, passwordEncoder, s3Service, s3Buckets);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void getAllCustomers() {
        // When
        underTest.getAllCustomers();

        // Then
        Mockito.verify(customerDao).selectAllCustomers();
    }

    @Test
    void canGetCustomer() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        CustomerDTO expected = customerDTOMapper.apply(customer);
        // When
        CustomerDTO actual = underTest.getCustomer(id);

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void willThrowWhenGetCustomerReturnsEmptyOptional() {
        // Given
        int id = 10;

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> underTest.getCustomer(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("customer with id [%s] not found".formatted(id));
    }

    @Test
    void addCustomer() {
        // Given
        String email = "alex@gmail.com";

        Mockito.when(customerDao.existsPersonWithEmail(email)).thenReturn(false);

        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Alex", email, "password", 19, Gender.MALE
        );

        String passwordHash = "$5554ml;f;lsd";

        Mockito.when(passwordEncoder.encode(request.password())).thenReturn(passwordHash);

        // When
        underTest.addCustomer(request);

        // Then
        ArgumentCaptor<Customer> customerArgumentCaptor = ArgumentCaptor.forClass(
                Customer.class
        );

        Mockito.verify(customerDao).insertCustomer(customerArgumentCaptor.capture());

        Customer capturedCustomer = customerArgumentCaptor.getValue();

        assertThat(capturedCustomer.getId()).isNull();
        assertThat(capturedCustomer.getName()).isEqualTo(request.name());
        assertThat(capturedCustomer.getEmail()).isEqualTo(request.email());
        assertThat(capturedCustomer.getAge()).isEqualTo(request.age());
        assertThat(capturedCustomer.getPassword()).isEqualTo(passwordHash);
    }

    @Test
    void willThrowWhenEmailExistsWhileAddingACustomer() {
        // Given
        String email = "alex@gmail.com";

        Mockito.when(customerDao.existsPersonWithEmail(email)).thenReturn(true);

        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "Alex", email, "password", 19, Gender.MALE
        );

        // When
        assertThatThrownBy(() -> underTest.addCustomer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("email already taken");

        // Then
        Mockito.verify(customerDao, Mockito.never()).insertCustomer(any());
    }

    @Test
    void deleteCustomerById() {
        // Given
        int id = 10;

        Mockito.when(customerDao.existsPersonWithId(id)).thenReturn(true);

        // When
        underTest.deleteCustomerById(id);

        // Then
        Mockito.verify(customerDao).deleteCustomerById(id);
    }

    @Test
    void willThrowWhenDeleteCustomerByIdNotExists() {
        // Given
        int id = 10;

        Mockito.when(customerDao.existsPersonWithId(id)).thenReturn(false);

        // When
        assertThatThrownBy(() -> underTest.deleteCustomerById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessage("customer with id [%s] not found".formatted(id));

        // Then
        Mockito.verify(customerDao, Mockito.never()).deleteCustomerById(id);
    }

    @Test
    void canUpdateAllCustomerProperties() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        String newEmail = "alexandro@amigocode.com";
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                "Alexandro", newEmail, 23);

        Mockito.when(customerDao.existsPersonWithEmail(newEmail)).thenReturn(false);

        // When
        underTest.updateCustomer(id, updateRequest);

        // Then
        ArgumentCaptor<Customer> customerArgumentCaptor =
                ArgumentCaptor.forClass(Customer.class);

        Mockito.verify(customerDao).updateCustomer(customerArgumentCaptor.capture());
        Customer capturedCustomer = customerArgumentCaptor.getValue();

        assertThat(capturedCustomer.getName()).isEqualTo(updateRequest.name());
        assertThat(capturedCustomer.getEmail()).isEqualTo(updateRequest.email());
        assertThat(capturedCustomer.getAge()).isEqualTo(updateRequest.age());
    }

    @Test
    void canUpdateOnlyCustomerName() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                "Alexandro", null, null);

        // When
        underTest.updateCustomer(id, updateRequest);

        // Then
        ArgumentCaptor<Customer> customerArgumentCaptor =
                ArgumentCaptor.forClass(Customer.class);

        Mockito.verify(customerDao).updateCustomer(customerArgumentCaptor.capture());
        Customer capturedCustomer = customerArgumentCaptor.getValue();

        assertThat(capturedCustomer.getName()).isEqualTo(updateRequest.name());
        assertThat(capturedCustomer.getEmail()).isEqualTo(customer.getEmail());
        assertThat(capturedCustomer.getAge()).isEqualTo(customer.getAge());
    }

    @Test
    void canUpdateOnlyCustomerEmail() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        String newEmail = "alexandro@amigocode.com";
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                null, newEmail, null);

        Mockito.when(customerDao.existsPersonWithEmail(newEmail)).thenReturn(false);

        // When
        underTest.updateCustomer(id, updateRequest);

        // Then
        ArgumentCaptor<Customer> customerArgumentCaptor =
                ArgumentCaptor.forClass(Customer.class);

        Mockito.verify(customerDao).updateCustomer(customerArgumentCaptor.capture());
        Customer capturedCustomer = customerArgumentCaptor.getValue();

        assertThat(capturedCustomer.getName()).isEqualTo(customer.getName());
        assertThat(capturedCustomer.getEmail()).isEqualTo(newEmail);
        assertThat(capturedCustomer.getAge()).isEqualTo(customer.getAge());
    }

    @Test
    void canUpdateOnlyCustomerAge() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                null, null, 22);

        // When
        underTest.updateCustomer(id, updateRequest);

        // Then
        ArgumentCaptor<Customer> customerArgumentCaptor =
                ArgumentCaptor.forClass(Customer.class);

        Mockito.verify(customerDao).updateCustomer(customerArgumentCaptor.capture());
        Customer capturedCustomer = customerArgumentCaptor.getValue();

        assertThat(capturedCustomer.getName()).isEqualTo(customer.getName());
        assertThat(capturedCustomer.getEmail()).isEqualTo(customer.getEmail());
        assertThat(capturedCustomer.getAge()).isEqualTo(updateRequest.age());
    }

    @Test
    void willThrowWhenTryingToUpdateCustomerEmailWhenAlreadyTaken() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        String newEmail = "alexandro@amigocode.com";
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                null, newEmail, null);

        Mockito.when(customerDao.existsPersonWithEmail(newEmail)).thenReturn(true);

        // When
        assertThatThrownBy(() -> underTest.updateCustomer(id, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("email already taken");

        // Then
        Mockito.verify(customerDao, Mockito.never()).updateCustomer(any());
    }

    @Test
    void willThrowWhenCustomerUpdateHasNoChanges() {
        // Given
        int id = 10;
        Customer customer = new Customer(
                id, "Alex", "alex@gmail.com", "password", 19,
                Gender.MALE);

        Mockito.when(customerDao.selectCustomerById(id)).thenReturn(Optional.of(customer));

        String newEmail = "alexandro@amigocode.com";
        CustomerUpdateRequest updateRequest = new CustomerUpdateRequest(
                customer.getName(), customer.getEmail(), customer.getAge());

        // When
        assertThatThrownBy(() -> underTest.updateCustomer(id, updateRequest))
                .isInstanceOf(RequestValidationException.class)
                .hasMessage("no data changes found");

        // Then
        Mockito.verify(customerDao, Mockito.never()).updateCustomer(any());
    }

    @Test
    void canUploadProfileImage() {
        // Given
        int customerId = 10;

        Mockito.when(customerDao.existsPersonWithId(customerId)).thenReturn(true);

        byte[] bytes = "Hello World".getBytes();
        MultipartFile multipartFile = new MockMultipartFile(
                "file", bytes
        );

        String bucket = "customer-bucket";
        Mockito.when(s3Buckets.getCustomer()).thenReturn(bucket);

        // When
        underTest.uploadCustomerProfileImage(
                customerId, multipartFile
        );

        // Then
        ArgumentCaptor<String> profileImageIdArgumentCaptor =
                ArgumentCaptor.forClass(String.class);

        Mockito.verify(customerDao).updateCustomerProfileImageId(
                profileImageIdArgumentCaptor.capture(),
                eq(customerId)
        );

        Mockito.verify(s3Service).putObject(
                bucket,
                "profile-images/%s/%s".formatted(
                        customerId, profileImageIdArgumentCaptor.getValue()),
                bytes
        );
    }

    @Test
    void cannotUploadProfileImageWhenCustomerDoesNotExist() {
        // Given
        int customerId = 10;

        Mockito.when(customerDao.existsPersonWithId(customerId)).thenReturn(false);

        // When
        assertThatThrownBy(() ->
            underTest.uploadCustomerProfileImage(customerId,
                    Mockito.mock(MultipartFile.class))
        ).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("customer with id ["+ customerId +"] not found");

        // Then
        Mockito.verify(customerDao).existsPersonWithId(customerId);
        Mockito.verifyNoMoreInteractions(customerDao);
        Mockito.verifyNoInteractions(s3Buckets);
        Mockito.verifyNoInteractions(s3Service);
    }

    @Test
    void cannotUploadProfileImageWhenExceptionThrown() throws IOException {
        // Given
        int customerId = 10;

        Mockito.when(customerDao.existsPersonWithId(customerId)).thenReturn(true);

        MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
        Mockito.when(multipartFile.getBytes()).thenThrow(IOException.class);

        String bucket = "customer-bucket";
        Mockito.when(s3Buckets.getCustomer()).thenReturn(bucket);

        // When
        assertThatThrownBy(() -> underTest.uploadCustomerProfileImage(
                customerId, multipartFile
        )).isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to upload profile image")
                .hasRootCauseInstanceOf(IOException.class);

        // Then
        Mockito.verify(customerDao, Mockito.never()).updateCustomerProfileImageId(
                any(),
                any()
        );
    }

    @Test
    void canDownloadProfileImage() {
        // Given
        int customerId = 10;
        String profileImageId = "2222";
        Customer customer = new Customer(
                customerId,
                "Alex",
                "alex@gmail.com",
                "password",
                19,
                Gender.MALE,
                profileImageId
        );
        Mockito.when(customerDao.selectCustomerById(customerId))
                .thenReturn(Optional.of(customer));

        String bucket = "customer-bucket";
        Mockito.when(s3Buckets.getCustomer()).thenReturn(bucket);

        byte[] expectedImage = "image".getBytes();
        Mockito.when(s3Service.getObject(
                bucket,
                "profile-images/%s/%s".formatted(customerId, profileImageId)
        )).thenReturn(expectedImage);

        // When
        byte[] actualImage = underTest.getCustomerProfileImage(customerId);

        // Then
        assertThat(actualImage).isEqualTo(expectedImage);
    }

    @Test
    void cannotDownloadWhenNoProfileImageId() {
        // Given
        int customerId = 10;
        Customer customer = new Customer(
                customerId,
                "Alex",
                "alex@gmail.com",
                "password",
                19,
                Gender.MALE
        );

        Mockito.when(customerDao.selectCustomerById(customerId))
                .thenReturn(Optional.of(customer));

        // When
        // Then
        assertThatThrownBy(() -> {
            underTest.getCustomerProfileImage(customerId);
        }).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("customer with id [%s] profile image not found"
                        .formatted(customerId));
        Mockito.verifyNoInteractions(s3Buckets);
        Mockito.verifyNoInteractions(s3Service);
    }

    @Test
    void cannotDownloadProfileImageWhenCustomerDoesNotExist() {
        // Given
        int customerId = 10;

        Mockito.when(customerDao.selectCustomerById(customerId))
                .thenReturn(Optional.empty());

        // When
        // Then
        assertThatThrownBy(() -> {
            underTest.getCustomerProfileImage(customerId);
        }).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("customer with id [%s] not found"
                        .formatted(customerId));

        Mockito.verifyNoInteractions(s3Buckets);
        Mockito.verifyNoInteractions(s3Service);
    }
}
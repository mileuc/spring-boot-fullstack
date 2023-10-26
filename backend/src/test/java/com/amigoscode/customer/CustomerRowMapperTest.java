package com.amigoscode.customer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CustomerRowMapperTest {

    @Test
    void mapRow() throws SQLException {
        // Given
        CustomerRowMapper customerRowMapper = new CustomerRowMapper();
        ResultSet resultSet = mock(ResultSet.class);
        Mockito.when(resultSet.getInt("id")).thenReturn(1);
        Mockito.when(resultSet.getInt("age")).thenReturn(19);
        Mockito.when(resultSet.getString("name")).thenReturn("Jameela");
        Mockito.when(resultSet.getString("email")).thenReturn("jameela@gmail.com");
        Mockito.when(resultSet.getString("gender")).thenReturn("FEMALE");
        Mockito.when(resultSet.getString("password")).thenReturn("password");
        Mockito.when(resultSet.getString("profile_image_id")).thenReturn("22222");

        // When
        Customer actual = customerRowMapper.mapRow(resultSet, 1);

        // Then
        Customer expected = new Customer(
                1, "Jameela", "jameela@gmail.com", "password", 19,
                Gender.FEMALE, "22222");
        assertThat(actual).isEqualTo(expected);
    }
}
package com.nttdata.banking.credits.model;

import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Class Client.
 * Credit microservice class Client.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Client {

    @Id
    private String idClient;
    private String clientType;
    private String documentType;
    private String documentNumber;

}
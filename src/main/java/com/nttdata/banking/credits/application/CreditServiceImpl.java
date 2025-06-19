package com.nttdata.banking.credits.application;

import com.nttdata.banking.credits.dto.CreditDto;
import com.nttdata.banking.credits.infrastructure.ClientRepository;
import com.nttdata.banking.credits.infrastructure.LoanRepository;
import com.nttdata.banking.credits.infrastructure.MovementRepository;
import com.nttdata.banking.credits.model.Client;
import com.nttdata.banking.credits.util.Constants;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;
import com.nttdata.banking.credits.model.Credit;
import org.springframework.beans.factory.annotation.Autowired;
import com.nttdata.banking.credits.infrastructure.CreditRepository;
import com.nttdata.banking.credits.exception.ResourceNotFoundException;
import java.time.LocalDateTime;

@Slf4j
@Service
public class CreditServiceImpl implements CreditService {

    @Autowired
    private CreditRepository creditRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private MovementRepository movementRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Override
    public Flux<Credit> findAll() {
        return creditRepository.findAll();
    }

    @Override
    public Mono<Credit> findById(String idCredit) {
        return Mono.just(idCredit)
                .flatMap(creditRepository::findById)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Credito", "IdCredito", idCredit)));
    }

    @Override
    public Mono<Credit> save(CreditDto creditDto) {
        return clientRepository.findClientByDni(String.valueOf(creditDto.getDocumentNumber()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cliente", "DocumentNumber", String.valueOf(creditDto.getDocumentNumber()))))
                .flatMap(client -> {
                    return this.validateCreditType(creditDto, client)
                            .flatMap(at -> {
                                if (at.equals(true)) {
                                    return creditDto.mapperToCredit(client)
                                            .flatMap(ba -> {
                                                log.info("sg MapperToCredit-------: ");
                                                return creditRepository.save(ba);
                                            });
                                } else {
                                    return Mono.error(new ResourceNotFoundException("Tarjeta de credito", "CreditType", creditDto.getCreditType()));
                                }

                            });
                });
    }

    @Override
    public Mono<Credit> update(CreditDto creditDto, String idCredit) {

        return clientRepository.findClientByDni(String.valueOf(creditDto.getDocumentNumber()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Cliente", "DocumentNumber", String.valueOf(creditDto.getDocumentNumber()))))
                .flatMap(client -> {
                    return creditDto.validateFields()
                            .flatMap(at -> {
                                if (at.equals(true)) {
                                    return creditRepository.findById(idCredit)
                                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Credit", "IdCredito", idCredit)))
                                            .flatMap(c -> {
                                                c.setClient(client);
                                                c.setCreditNumber(creditDto.getCreditNumber() == null ? c.getCreditNumber() : creditDto.getCreditNumber());
                                                c.setCreditType(creditDto.getCreditType() == null ? c.getCreditType() : creditDto.getCreditType());
                                                c.setCreditLineAmount(creditDto.getCreditLineAmount() == null ? c.getCreditLineAmount() : creditDto.getCreditLineAmount());
                                                c.setCurrency(creditDto.getCurrency() == null ? c.getCurrency() : creditDto.getCurrency());
                                                c.setStatus(creditDto.getStatus() == null ? c.getStatus() : creditDto.getStatus());
                                                c.setBalance(creditDto.getBalance() == null ? c.getBalance() : creditDto.getBalance());
                                                return creditRepository.save(c);

                                            });
                                } else {
                                    return Mono.error(new ResourceNotFoundException("Tarjeta de Credito", "CreditType", creditDto.getCreditType()));
                                }
                            });
                });
    }

    @Override
    public Mono<Void> delete(String idCredit) {
        return creditRepository.findById(idCredit)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Credito", "IdCredito", idCredit)))
                .flatMap(creditRepository::delete);
    }

    @Override
    public Flux<Credit> findByDocumentNumber(String documentNumber) {
        log.info("Inicio----findByDocumentNumber-------documentNumber : " + documentNumber);
        return creditRepository.findByCreditClient(documentNumber);
    }

    @Override
    public Mono<Credit> findByCreditNumber(Integer creditNumber) {
        return Mono.just(creditNumber)
                .flatMap(creditRepository::findCreditByCreditNumber)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Credito", "creditNumber", creditNumber.toString())));
    }

    @Override
    public Mono<CreditDto> findMovementsByDocumentNumber(String documentNumber) {
        log.info("Inicio----findMovementsByDocumentNumber-------: ");
        log.info("Inicio----findMovementsByDocumentNumber-------documentNumber : " + documentNumber);
        return creditRepository.findByDocumentNumber(documentNumber)
                .flatMap(d -> {
                    log.info("Inicio----findMovementsByCreditNumber-------: ");
                    return movementRepository.findMovementsByCreditNumber(d.getCreditNumber().toString())
                            .collectList()
                            .flatMap(m -> {
                                log.info("----findMovementsByCreditNumber setMovements-------: ");
                                d.setMovements(m);
                                return Mono.just(d);
                            });
                });
    }

    @Override
    public Flux<Credit> findCreditByDocumentNumber(String documentNumber) {
        log.info("Inicio----findCreditByDocumentNumber-------: ");
        log.info("Inicio----findCreditByDocumentNumber-------documentNumber : " + documentNumber);
        return creditRepository.findByCreditClient(documentNumber);
    }

    //Validar CreditType
    public Mono<Boolean> validateCreditType(CreditDto creditDto, Client client) {
        log.info("Inicio validateCreditType-------creditDto: " + creditDto.toString());
        log.info("Inicio validateCreditType-------client: " + client.toString());
        return Mono.just(creditDto.getCreditType()).flatMap(ct -> {
            //Boolean isOk = false;
            if (creditDto.getCreditType().equalsIgnoreCase("Personal")) { //Tarjeta de credito personal.
                return validateCreditDebt(client.getDocumentNumber(), "Personal").flatMap(vcd -> {
                    if ((vcd).equals(true)) {
                        log.info("if validateCreditDebt-------: ");
                        return validateLoanDebt(client.getDocumentNumber(), "Personal");
                    } else {
                        log.info("else validateCreditDebt-------: ");
                        return Mono.just(false);
                    }
                });
            } else if (creditDto.getCreditType().equalsIgnoreCase("Business")) { //Tarjeta de credito Empresarial.
                return validateCreditDebt(client.getDocumentNumber(), "Business").flatMap(vcd -> {
                    if (vcd.equals(true)) {
                        return validateLoanDebt(client.getDocumentNumber(), "Business");
                    } else {
                        return Mono.just(false);
                    }
                });
            } else {
                log.info("Inicio validateCreditType-------else: ");
                return Mono.error(new ResourceNotFoundException("Tarjeta de credito", "CreditType", creditDto.getCreditType()));
            }
            //log.info("Fin validateCreditType-------: ");
            //return Mono.just(isOk);
        });
    }

    //Si tiene deuda retorna false
    public Mono<Boolean> validateCreditDebt(String documentNumber, String creditType) {

        log.info("Inicio----validateCreditDebt-------: ");
        log.info("Inicio----validateCreditDebt-------creditType: " + creditType);
        log.info("Inicio----validateCreditDebt-------documentNumber : " + documentNumber);
        LocalDateTime datetime = LocalDateTime.now();
        return creditRepository.findByCreditClient(documentNumber)
                .collectList()
                .doOnNext(x -> log.info("Inicio----findByCreditClient-------doOnNext: "))
                .flatMap(l -> {
                    log.info("Inicio----findByCreditClient-------libre: ");
                    log.info("Inicio----validateCreditDebt-------l: " + (l == null ? "" : l.toString()));
                    if (creditType.equals("Personal")) {
                        log.info("Inicio----validateCreditDebt-------Personal: ");
                        if (l.size() == Constants.ZERO || l == null) {
                            log.info("Inicio----validateCreditDebt-------if1: ");
                            return Mono.just(true);
                        } else {
                            log.info("Inicio----validateCreditDebt-------else1: ");
                            if (datetime.isBefore(l.get(0).getExpirationDate())) {
                                log.info("Inicio----validateCreditDebt-------if2: ");
                                return Mono.just(true);//No se vence
                            } else {
                                log.info("Inicio----validateCreditDebt-------else2: ");
                                return Mono.just(false);//Ya se vencio
                            }
                        }
                    } else if (creditType.equals("Business")) {
                        log.info("Inicio----validateCreditDebt-------Business: ");
                        if (l.size() == Constants.ZERO || l == null) {
                            return Mono.just(true);
                        } else {
                            if (datetime.isBefore(l.get(0).getExpirationDate())) {
                                return Mono.just(true);//No se vence
                            } else {
                                return Mono.just(false);//Ya se vencio
                            }
                        }
                    } else {
                        log.info("Inicio----validateCreditDebt-------else0: ");
                        return Mono.just(false);
                    }
                });
    }

    //Si tiene deuda return false sino true
    public Mono<Boolean> validateLoanDebt(String documentNumber, String loanType) {

        log.info("Inicio----validateLoanDebt-------: ");
        log.info("Inicio----validateLoanDebt-------documentNumber : " + documentNumber);
        LocalDateTime datetime = LocalDateTime.now();
        return loanRepository.findLoansByDocumentNumber(documentNumber)
                .collectList()
                .flatMap(c -> {
                    if (loanType.equals("Personal")) {
                        if (c.size() == Constants.ZERO || c == null) {
                            return Mono.just(true);
                        } else {
                            if (datetime.isBefore(c.get(0).getExpirationDate())) {
                                return Mono.just(true);//No se vence
                            } else {
                                return Mono.just(false);//Ya se vencio
                            }
                        }
                    } else if (loanType.equals("Business")) {
                        if (c.size() == Constants.ZERO || c == null) {
                            return Mono.just(true);
                        } else {
                            if (datetime.isBefore(c.get(0).getExpirationDate())) {
                                return Mono.just(true);//No se vence
                            } else {
                                return Mono.just(false);//Ya se vencio
                            }
                        }
                    }
                    return Mono.just(true);
                });
    }

}

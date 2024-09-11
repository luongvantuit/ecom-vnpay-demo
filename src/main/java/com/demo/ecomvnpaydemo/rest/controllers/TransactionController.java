package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;

    public TransactionController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = "/{transactionId}", method = RequestMethod.GET)
    public ModelAndView show(Model model, @PathVariable(value = "transactionId") String transactionId) {
        Optional<Transaction> optionalTransaction = transactionRepository.findById(UUID.fromString(transactionId));
        if (optionalTransaction.isEmpty()) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("transaction", optionalTransaction.get());
        return new ModelAndView("transaction");
    }

}

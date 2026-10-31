package com.pcdoctor.service;

import org.springframework.stereotype.Service;

@Service
public class ReparacionService {
    private final ComandoWindowsService comandos;

    public ReparacionService(ComandoWindowsService comandos) {
        this.comandos = comandos;
    }


    public String renovarIp() {
        return comandos.ejecutar("cmd", "/c", "ipconfig", "/release") +
                System.lineSeparator() +
                comandos.ejecutar("cmd", "/c", "ipconfig", "/renew");
    }

    public String resetWinsock() {
        return comandos.ejecutar("cmd", "/c", "netsh", "winsock", "reset");
    }


}

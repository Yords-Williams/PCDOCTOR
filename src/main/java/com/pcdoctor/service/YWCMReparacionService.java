package com.pcdoctor.service;

import org.springframework.stereotype.Service;

@Service
public class YWCMReparacionService {
    private final YWCMComandoWindowsService YWCMcomandos;

    public YWCMReparacionService(YWCMComandoWindowsService YWCMcomandos) {
        this.YWCMcomandos = YWCMcomandos;
    }


    public String YWCMrenovarIp() {
        return YWCMcomandos.YWCMejecutar("cmd", "/c", "ipconfig", "/release") +
                System.lineSeparator() +
                YWCMcomandos.YWCMejecutar("cmd", "/c", "ipconfig", "/renew");
    }

    public String YWCMresetWinsock() {
        return YWCMcomandos.YWCMejecutar("cmd", "/c", "netsh", "winsock", "reset");
    }


}

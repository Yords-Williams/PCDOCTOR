package com.pcdoctor.service;

import org.springframework.stereotype.Service;

@Service
public class ReparacionService {
    private final ComandoWindowsService comandos;

    public ReparacionService(ComandoWindowsService comandos) {
        this.comandos = comandos;
    }

    public String limpiarDns() {
        return comandos.ejecutar("cmd", "/c", "ipconfig", "/flushdns");
    }

    public String renovarIp() {
        return comandos.ejecutar("cmd", "/c", "ipconfig", "/release") +
                System.lineSeparator() +
                comandos.ejecutar("cmd", "/c", "ipconfig", "/renew");
    }

    public String resetWinsock() {
        return comandos.ejecutar("cmd", "/c", "netsh", "winsock", "reset");
    }

    public String comprobarArchivosWindows() {
        return comandos.ejecutar("cmd", "/c", "sfc", "/scannow");
    }

    public String repararImagenWindows() {
        return comandos.ejecutar("cmd", "/c", "DISM", "/Online", "/Cleanup-Image", "/RestoreHealth");
    }

    public String abrirLimpiezaDisco() {
        return comandos.ejecutar("cmd", "/c", "cleanmgr");
    }

    public String abrirAdministradorTareas() {
        return comandos.ejecutar("cmd", "/c", "taskmgr");
    }

    public String abrirConfiguracionRed() {
        return comandos.ejecutar("cmd", "/c", "start", "", "ms-settings:network-status");
    }
}

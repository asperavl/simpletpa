package com.jellypudding.simpleTPA;

import com.jellypudding.simpleTPA.commands.TpaCommand;
import com.jellypudding.simpleTPA.commands.TpacancelCommand;
import com.jellypudding.simpleTPA.commands.TpacceptCommand;
import com.jellypudding.simpleTPA.commands.TpdenyCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SimpleTPA extends JavaPlugin {

    private RequestManager requestManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        requestManager = new RequestManager(this);

        registerCommands();

        new Metrics(this, 27552);

        getLogger().info("SimpleTPA has been enabled.");
    }

    @Override
    public void onDisable() {
        if (requestManager != null) {
            requestManager.shutdown();
        }
        getLogger().info("SimpleTPA has been disabled.");
    }

    private void registerCommands() {
        TpaCommand tpa = new TpaCommand(requestManager);
        TpacceptCommand tpaccept = new TpacceptCommand(requestManager);
        TpdenyCommand tpdeny = new TpdenyCommand(requestManager);
        TpacancelCommand tpacancel = new TpacancelCommand(requestManager);

        Objects.requireNonNull(getCommand("tpa")).setExecutor(tpa);
        Objects.requireNonNull(getCommand("tpa")).setTabCompleter(tpa);
        Objects.requireNonNull(getCommand("tpaccept")).setExecutor(tpaccept);
        Objects.requireNonNull(getCommand("tpaccept")).setTabCompleter(tpaccept);
        Objects.requireNonNull(getCommand("tpdeny")).setExecutor(tpdeny);
        Objects.requireNonNull(getCommand("tpdeny")).setTabCompleter(tpdeny);
        Objects.requireNonNull(getCommand("tpacancel")).setExecutor(tpacancel);
        Objects.requireNonNull(getCommand("tpacancel")).setTabCompleter(tpacancel);
    }

    public RequestManager getRequestManager() {
        return requestManager;
    }
}

package com.elmakers.mine.bukkit.utility;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.elmakers.mine.bukkit.api.event.MagicErrorEvent;
import com.elmakers.mine.bukkit.api.event.MagicWarningEvent;

public class MagicLogger extends ColoredLogger {

    private String context = null;
    private boolean capture = false;
    private final Set<LogMessage> warnings = new LinkedHashSet<>();
    private final Set<LogMessage> errors = new LinkedHashSet<>();

    private int pendingWarningCount = 0;
    private int pendingErrorCount = 0;
    private long lastMessageSent;

    public MagicLogger(Logger delegate) {
        super(delegate);
        lastMessageSent = System.currentTimeMillis();
    }

    @Override
    public void log(LogRecord record) {
        if (!capture || (!record.getLevel().equals(Level.WARNING) && !record.getLevel().equals(Level.SEVERE)) || record.getThrown() != null) {
            super.log(record);
        }

        LogMessage logMessage = new LogMessage(context, record.getMessage().replace("[Magic] ", ""));
        if (record.getLevel().equals(Level.WARNING)) {
            if (!capture) {
                pendingWarningCount++;
            }
            if (warnings.size() == 50) {
                warnings.remove(warnings.iterator().next());
            }

            warnings.add(logMessage);
            Bukkit.getPluginManager().callEvent(new MagicWarningEvent(record, context, pendingWarningCount, capture));
        }
        else if (record.getLevel().equals(Level.SEVERE)) {
            if (!capture) {
                pendingErrorCount++;
            }
            if (errors.size() == 50) {
                errors.remove(errors.iterator().next());
            }

            errors.add(logMessage);
            Bukkit.getPluginManager().callEvent(new MagicErrorEvent(record, context, pendingErrorCount, capture));
        }
    }

    public void enableCapture(boolean enable) {
        this.capture = enable;
        this.context = null;
        this.warnings.clear();
        this.errors.clear();
    }

    public void setContext(String context) {
        this.context = context;
    }

    public List<LogMessage> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<LogMessage> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public boolean isCapturing() {
        return capture;
    }

    public void notify(Messages messages, CommandSender sender) {
        if (pendingErrorCount == 0 && pendingWarningCount == 0) return;
        long timeSince = System.currentTimeMillis() - lastMessageSent;

        String sinceMessage = messages.getTimeDescription(timeSince, "description", "cooldown");
        String messageKey = "logs.notify_errors";
        if (pendingErrorCount == 0) {
            messageKey = "logs.notify_warnings";
        } else if (pendingWarningCount != 0) {
            messageKey = "logs.notify_errors_and_warnings";
        }
        String message = messages.get(messageKey);
        message = message
            .replace("$time", sinceMessage)
            .replace("$warnings", Integer.toString(pendingWarningCount))
            .replace("$errors", Integer.toString(pendingErrorCount));
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    public void checkNotify(Messages messages) {
        if (pendingErrorCount == 0 && pendingWarningCount == 0) return;
        boolean sent = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("Magic.notify")) {
                notify(messages, player);
                String message = messages.get("logs.notify_instructions");
                if (!message.isEmpty()) {
                    player.sendMessage(message);
                }
                sent = true;
            }
        }
        if (sent) {
            clearNotify();
        }
    }

    public void clearNotify() {
        pendingErrorCount = 0;
        pendingWarningCount = 0;
        lastMessageSent = System.currentTimeMillis();
    }
}

package org.black_ixx.playerpoints.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.manager.LocaleManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.permissions.Permissible;
import org.bukkit.util.StringUtil;

/**
 * Abstract class to handle the majority of the logic dealing with commands.
 * Allows for a nested structure of commands.
 */
public abstract class CommandHandler implements TabExecutor, NamedExecutor {
    /**
     * Registered commands for this handler.
     */
    protected final Map<String, PointsCommand> registeredCommands = new HashMap<>();
    /**
     * Registered subcommands and the handler associated with it.
     */
    protected final Map<String, CommandHandler> registeredHandlers = new HashMap<>();
    /**
     * Root plugin so that commands and handlers have access to the information.
     */
    protected PlayerPoints plugin;

    /**
     * Command name.
     */
    protected String cmd;

    /**
     * Constructor.
     *
     * @param plugin - Root plugin.
     */
    public CommandHandler(PlayerPoints plugin, String cmd) {
        this.plugin = plugin;
        this.cmd = cmd;
    }

    /**
     * Register a command with an execution handler.
     *
     * @param label   - Command to listen for.
     * @param command - Execution handler that will handle the logic behind the
     *                command.
     */
    public void registerCommand(String label, PointsCommand command) {
        if (this.registeredCommands.containsKey(label)) {
            this.plugin.getLogger().warning("Replacing existing command for: " + label);
        }
        this.registeredCommands.put(label, command);
    }

    /**
     * Unregister a command for this handler.
     *
     * @param label - Command to stop handling.
     */
    public void unregisterCommand(String label) {
        this.registeredCommands.remove(label);
    }

    /**
     * Register a subcommand with a command handler.
     *
     * @param handler - Command handler.
     */
    public void registerHandler(CommandHandler handler) {
        if (this.registeredHandlers.containsKey(handler.getCommand())) {
            this.plugin.getLogger().warning("Replacing existing handler for: " + handler.getCommand());
        }
        this.registeredHandlers.put(handler.getCommand(), handler);
    }

    /**
     * Unregister a subcommand.
     *
     * @param label - Subcommand to remove.
     */
    public void unregisterHandler(String label) {
        this.registeredHandlers.remove(label);
    }

    /**
     * Command loop that will go through the linked handlers until it finds the
     * appropriate handler or command execution handler to do the logic for.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.noArgs(sender);
            return true;
        }

        String subcmd = args[0].toLowerCase();

        // Check known handlers first and pass to them
        CommandHandler handler = this.registeredHandlers.get(subcmd);
        if (handler != null) {
            // Make sure they have permission
            if (!handler.hasPermission(sender)) {
                this.plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
                return true;
            }
            handler.onCommand(sender, command, label, this.shortenArgs(args));
            return true;
        }

        // Its our command, so handle it if its registered.
        PointsCommand subCommand = this.registeredCommands.get(subcmd);
        if (subCommand == null) {
            this.unknownCommand(sender, args);
            return true;
        }

        // Make sure they have permission
        if (!subCommand.hasPermission(sender)) {
            this.plugin.getManager(LocaleManager.class).sendMessage(sender, "no-permission");
            return true;
        }

        // Execute command
        try {
            subCommand.execute(this.plugin, sender, this.shortenArgs(args));
        } catch (ArrayIndexOutOfBoundsException e) {
            sender.sendMessage(ChatColor.RED + "A PlayerPoints error occurred while executing that command. Did you enter an invalid parameter?");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0)
            return Collections.emptyList();

        String subcmd = args[0].toLowerCase();
        if (args.length == 1) {
            // Complete against command names the sender has permission for
            List<String> commandNames = new ArrayList<>();

            commandNames.addAll(this.registeredHandlers.entrySet().stream()
                    .filter(x -> x.getValue().hasPermission(sender))
                    .map(Map.Entry::getKey).collect(Collectors.toList()));

            commandNames.addAll(this.registeredCommands.entrySet().stream()
                    .filter(x -> x.getValue().hasPermission(sender))
                    .map(Map.Entry::getKey).collect(Collectors.toList()));

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(subcmd, commandNames, completions);
            return completions;
        }

        // Try to find a handler to pass to
        CommandHandler handler = this.registeredHandlers.get(subcmd);
        if (handler != null && handler.hasPermission(sender))
            return handler.onTabComplete(sender, command, alias, this.shortenArgs(args));

        // Look for a command to pass to
        PointsCommand subCommand = this.registeredCommands.get(subcmd);
        if (subCommand != null && subCommand.hasPermission(sender))
            return subCommand.tabComplete(this.plugin, sender, this.shortenArgs(args));

        // No matching commands, return an empty list
        return Collections.emptyList();
    }

    /**
     * Method that is called on a CommandHandler if there is no additional
     * arguments given that specify a specific command.
     *
     * @param sender  - Sender of the command.
     */
    public abstract void noArgs(CommandSender sender);

    /**
     * Allow for the command handler to have special logic for unknown commands.
     * Useful for when expecting a player name parameter on a root command
     * handler command.
     *
     * @param sender  - Sender of the command.
     * @param args    - Arguments.
     */
    public abstract void unknownCommand(CommandSender sender, String[] args);

    /**
     * @return a combination of all executable commands and handlers sorted by name
     */
    public List<NamedExecutor> getExecutables() {
        List<NamedExecutor> executors = new ArrayList<>();
        executors.addAll(this.registeredHandlers.values());
        executors.addAll(this.registeredCommands.values());
        executors.sort(Comparator.comparing(NamedExecutor::getName));
        return executors;
    }

    @Override
    public String getName() {
        return this.cmd;
    }

    @Override
    public boolean hasPermission(Permissible permissible) {
        return true;
    }

    /**
     * Shortens the given string array by removing the first entry.
     *
     * @param args - Array to shorten.
     * @return Shortened array.
     */
    protected String[] shortenArgs(String[] args) {
        if (args.length == 0) {
            return args;
        }
        final List<String> argList = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        return argList.toArray(new String[0]);
    }

    /**
     * Get the command for this handler.
     *
     * @return Command
     */
    public String getCommand() {
        return this.cmd;
    }
}

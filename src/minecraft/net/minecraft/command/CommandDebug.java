package net.minecraft.command;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandDebug extends CommandBase
{
    private static final Logger logger = LogManager.getLogger();

    /** The time (in milliseconds) that profiling was started */
    private long profileStartTime;

    /** The tick number that profiling was started on */
    private int profileStartTick;

    /**
     * Gets the name of the command
     */
    public String getCommandName()
    {
        return "debug";
    }

    /**
     * Return the required permission level for this command.
     */
    public int getRequiredPermissionLevel()
    {
        return 3;
    }

    /**
     * Gets the usage string for the command.
     */
    public String getCommandUsage(ICommandSender sender)
    {
        return "commands.debug.usage";
    }

    /**
     * Callback when the command is invoked
     */
    public void processCommand(ICommandSender sender, String[] args) throws CommandException
    {
        if (args.length < 1)
        {
            throw new WrongUsageException("commands.debug.usage", new Object[0]);
        }
        else
        {
            if (args[0].equals("start"))
            {
                if (args.length != 1)
                {
                    throw new WrongUsageException("commands.debug.usage", new Object[0]);
                }

                notifyOperators(sender, this, "commands.debug.start", new Object[0]);
                MinecraftServer.getServer().enableProfiling();
                this.profileStartTime = MinecraftServer.getCurrentTimeMillis();
                this.profileStartTick = MinecraftServer.getServer().getTickCounter();
            }
            else
            {
                if (!args[0].equals("stop"))
                {
                    throw new WrongUsageException("commands.debug.usage", new Object[0]);
                }

                if (args.length != 1)
                {
                    throw new WrongUsageException("commands.debug.usage", new Object[0]);
                }

                long i = MinecraftServer.getCurrentTimeMillis();
                int j = MinecraftServer.getServer().getTickCounter();
                long k = i - this.profileStartTime;
                int l = j - this.profileStartTick;
                this.saveProfileResults(k, l);
                notifyOperators(sender, this, "commands.debug.stop", new Object[] {Float.valueOf((float)k / 1000.0F), Integer.valueOf(l)});
            }
        }
    }

    /**
     * Save the profiling results from the last profile
     */
    private void saveProfileResults(long timeSpan, int tickSpan)
    {
        File file1 = new File(MinecraftServer.getServer().getFile("debug"), "profile-results-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + ".txt");
        file1.getParentFile().mkdirs();

        try
        {
            FileWriter filewriter = new FileWriter(file1);
            filewriter.write(this.getProfileResults(timeSpan, tickSpan));
            filewriter.close();
        }
        catch (Throwable throwable)
        {
            logger.error("Could not save profiler results to " + file1, throwable);
        }
    }

    /**
     * Get the profiling results from the last profile
     */
    private String getProfileResults(long timeSpan, int tickSpan)
    {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("---- Minecraft Profiler Results ----\n");
        stringbuilder.append("// ");
        stringbuilder.append(getWittyComment());
        stringbuilder.append("\n\n");
        stringbuilder.append("Time span: ").append(timeSpan).append(" ms\n");
        stringbuilder.append("Tick span: ").append(tickSpan).append(" ticks\n");
        stringbuilder.append("// This is approximately ").append(String.format("%.2f", new Object[] {Float.valueOf((float)tickSpan / ((float)timeSpan / 1000.0F))})).append(" ticks per second. It should be ").append((int)20).append(" ticks per second\n\n");
        stringbuilder.append("--- BEGIN PROFILE DUMP ---\n\n");
        stringbuilder.append("--- END PROFILE DUMP ---\n\n");
        return stringbuilder.toString();
    }

    /**
     * Get a random witty comment
     */
    private static String getWittyComment()
    {
        String[] astring = new String[] {"Shiny numbers!", "Am I not running fast enough? :(", "I\'m working as hard as I can!", "Will I ever be good enough for you? :(", "Speedy. Zoooooom!", "Hello world", "40% better than a crash report.", "Now with extra numbers", "Now with less numbers", "Now with the same numbers", "You should add flames to things, it makes them go faster!", "Do you feel the need for... optimization?", "*cracks redstone whip*", "Maybe if you treated it better then it\'ll have more motivation to work faster! Poor server."};

        try
        {
            return astring[(int)(System.nanoTime() % (long)astring.length)];
        }
        catch (Throwable var2)
        {
            return "Witty comment unavailable :(";
        }
    }

    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos)
    {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, new String[] {"start", "stop"}): null;
    }
}

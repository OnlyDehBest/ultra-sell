package it.onlynelchilling.ultrasell.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

public final class SchedulerUtil {

    private static final boolean FOLIA;
    private static MethodHandle GET_ASYNC_SCHEDULER;
    private static MethodHandle ASYNC_RUN_NOW;
    private static MethodHandle GET_GLOBAL_SCHEDULER;
    private static MethodHandle GLOBAL_RUN;
    private static MethodHandle GLOBAL_RUN_AT_FIXED_RATE;
    private static MethodHandle ENTITY_GET_SCHEDULER;
    private static MethodHandle ENTITY_RUN;
    private static MethodHandle ENTITY_RUN_DELAYED;
    private static java.lang.reflect.Method SCHEDULED_TASK_CANCEL;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
        if (FOLIA) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                Class<?> globalScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Class<?> scheduledTask = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

                GET_ASYNC_SCHEDULER = lookup.findVirtual(Bukkit.getServer().getClass(), "getAsyncScheduler", MethodType.methodType(asyncScheduler));
                ASYNC_RUN_NOW = lookup.findVirtual(asyncScheduler, "runNow", MethodType.methodType(scheduledTask, Plugin.class, Consumer.class));
                GET_GLOBAL_SCHEDULER = lookup.findVirtual(Bukkit.getServer().getClass(), "getGlobalRegionScheduler", MethodType.methodType(globalScheduler));
                GLOBAL_RUN = lookup.findVirtual(globalScheduler, "run", MethodType.methodType(void.class, Plugin.class, Consumer.class));
                GLOBAL_RUN_AT_FIXED_RATE = lookup.findVirtual(globalScheduler, "runAtFixedRate", MethodType.methodType(scheduledTask, Plugin.class, Consumer.class, long.class, long.class));
                ENTITY_GET_SCHEDULER = lookup.findVirtual(Entity.class, "getScheduler", MethodType.methodType(entityScheduler));
                ENTITY_RUN = lookup.findVirtual(entityScheduler, "run", MethodType.methodType(scheduledTask, Plugin.class, Consumer.class, Runnable.class));
                ENTITY_RUN_DELAYED = lookup.findVirtual(entityScheduler, "runDelayed", MethodType.methodType(scheduledTask, Plugin.class, Consumer.class, Runnable.class, long.class));
                SCHEDULED_TASK_CANCEL = scheduledTask.getMethod("cancel");
            } catch (Exception e) {
                throw new RuntimeException("Failed to init Folia scheduler handles", e);
            }
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = GET_ASYNC_SCHEDULER.invoke(Bukkit.getServer());
                ASYNC_RUN_NOW.invoke(scheduler, plugin, (Consumer<?>) t -> task.run());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runSync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = GET_GLOBAL_SCHEDULER.invoke(Bukkit.getServer());
                GLOBAL_RUN.invoke(scheduler, plugin, (Consumer<?>) t -> task.run());
            } catch (Throwable e) {
                throw new RuntimeException("Failed to execute sync task", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (FOLIA) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                ENTITY_RUN.invoke(scheduler, plugin, (Consumer<?>) t -> task.run(), retired);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static Runnable runGlobalRepeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        long d = Math.max(1L, delayTicks);
        long p = Math.max(1L, periodTicks);
        if (FOLIA) {
            try {
                Object scheduler = GET_GLOBAL_SCHEDULER.invoke(Bukkit.getServer());
                Object scheduled = GLOBAL_RUN_AT_FIXED_RATE.invoke(scheduler, plugin, (Consumer<?>) t -> task.run(), d, p);
                return () -> {
                    try { SCHEDULED_TASK_CANCEL.invoke(scheduled); } catch (Throwable ignored) {}
                };
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, d, p);
        return t::cancel;
    }

    public static void runForEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks) {
        if (FOLIA) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                ENTITY_RUN_DELAYED.invoke(scheduler, plugin, (Consumer<?>) t -> task.run(), retired, delayTicks);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
}


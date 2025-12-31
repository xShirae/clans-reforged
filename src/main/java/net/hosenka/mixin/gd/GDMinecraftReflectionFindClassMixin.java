package net.hosenka.mixin.gd;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.griefdefender.lib.kyori.adventure.platform.forge.MinecraftReflection", remap = false)
public class GDMinecraftReflectionFindClassMixin {

    private static boolean printed;

    @Inject(method = "findClass([Ljava/lang/String;)Ljava/lang/Class;", at = @At("HEAD"), cancellable = true)
    private static void clansreforged$findClassMapped(String[] candidates, CallbackInfoReturnable<Class<?>> cir) {
        if (!printed) {
            printed = true;
            System.out.println("[ClansReforged] Patched GD MinecraftReflection.findClass() is running");
        }

        try {
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();

            for (String raw : candidates) {
                if (raw == null || raw.isBlank()) continue;

                // Try GD's raw candidate first
                Class<?> direct = tryLoad(raw);
                if (direct != null) {
                    cir.setReturnValue(direct);
                    return;
                }

                // Normalize to dotted FQCN
                String dotted;
                if (raw.startsWith("net/minecraft/")) dotted = raw.replace('/', '.');
                else if (raw.startsWith("net.minecraft.")) dotted = raw;
                else dotted = "net.minecraft." + raw;

                // Guess source namespace
                String srcNs = dotted.contains("class_") ? "intermediary" : "official";

                // Map to current runtime namespace (named in dev, intermediary in prod)
                String mapped = resolver.mapClassName(srcNs, dotted);

                Class<?> mappedCls = tryLoad(mapped);
                if (mappedCls != null) {
                    cir.setReturnValue(mappedCls);
                    return;
                }

                // Also try the normalized dotted name as-is
                Class<?> dottedCls = tryLoad(dotted);
                if (dottedCls != null) {
                    cir.setReturnValue(dottedCls);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }


        // Let GD's original method run.
    }

    private static Class<?> tryLoad(String name) {
        try {
            return Class.forName(name, false, GDMinecraftReflectionFindClassMixin.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }
}

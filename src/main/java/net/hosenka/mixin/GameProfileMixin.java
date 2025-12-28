package net.hosenka.mixin;

import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(GameProfile.class)
public class GameProfileMixin {

    @Mutable
    @Shadow(remap = false)
    @Final
    private UUID id;

    @Mutable
    @Shadow(remap = false)
    @Final
    private String name;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(UUID id, String name, CallbackInfo ci) {
        if (Boolean.getBoolean("fabric.development")) {
            this.id = UUID.fromString("87b2f421-f609-47f7-92a2-c79762b35997");
            this.name = "xShirayuki";
        }
    }
}

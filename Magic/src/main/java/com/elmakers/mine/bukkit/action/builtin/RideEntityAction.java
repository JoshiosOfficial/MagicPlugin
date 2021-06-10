package com.elmakers.mine.bukkit.action.builtin;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.action.BaseSpellAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.effect.SoundEffect;
import com.elmakers.mine.bukkit.magic.MagicMetaKeys;
import com.elmakers.mine.bukkit.spell.BaseSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.DeprecatedUtils;
import com.elmakers.mine.bukkit.utility.EntityMetadataUtils;
import com.elmakers.mine.bukkit.utility.SafetyUtils;

import de.slikey.effectlib.util.VectorUtils;

public class RideEntityAction extends BaseSpellAction
{
    private double moveDistance = 0;
    private double startSpeed = 0;
    private double minSpeed = 0;
    private double maxSpeed = 0;
    private double maxAcceleration = 0;
    private double maxDeceleration = 0;
    private double liftoffThrust = 0;
    private double crashDistance = 0;
    private double crashSpeed = 0;
    private int duration = 0;
    private int durationWarning = 0;
    private int liftoffDuration = 0;
    private int maxAscend;
    private int maxHeightAboveGround;
    private int heightCheckRadius;
    private int maxHeight;
    private int exemptionDuration;
    private double gravity;
    private double terminalVelocity;
    private boolean airControllable = false;
    private boolean controllable = false;
    private boolean pitchControllable = true;
    private double strafeControllable = 0;
    private double steerControllable = 0;
    private double jumpControllable = 0;
    private double jumpVelocity = 0;
    private double braking = 0;
    private double pitchOffset = 0;
    private double yawOffset = 0;
    private Double yDirection = null;
    private Collection<PotionEffect> crashEffects;
    private Collection<PotionEffect> warningEffects;
    private Collection<PotionEffect> ridingEffects;

    private SoundEffect sound = null;
    private int soundInterval = 1000;
    private float soundMaxVolume = 1;
    private float soundMinVolume = 1;
    private float soundMaxPitch = 1;
    private float soundMinPitch = 1;

    private long liftoffTime;
    private double speed;
    private boolean warningEffectsApplied;
    private long nextSoundPlay;
    protected boolean noTarget = true;
    private boolean noTargetPlayer = false;
    private Class<?> crashEntityType;
    private double crashDismountSpeed;
    private double crashEntityDistance;
    private double crashVelocityYOffset = 0;
    private double crashVelocity = 0;
    private double crashDamage = 0;
    private double crashVehicleDamage = 0;
    private double crashEntityVehicleDamage = 0;
    private double crashEntityDamage = 0;
    private double crashBraking = 0;
    private double crashEntityFOV = 0;
    private int fallProtection = 0;
    private boolean isPassenger;
    private Spell jumpSpell;
    private String[] jumpSpellParameters;
    private Vector gravityVelocity = new Vector();

    protected Vector direction;
    protected Entity mount;
    protected Location targetLocation;
    private boolean isAscending;
    private boolean isInAir;
    private String boostAttribute;

    @Override
    public void initialize(Spell spell, ConfigurationSection parameters) {
        super.initialize(spell, parameters);

        crashEffects = ConfigurationUtils.getPotionEffects(parameters.getConfigurationSection("crash_effects"));
        durationWarning = parameters.getInt("duration_warning", 0);
        warningEffects = ConfigurationUtils.getPotionEffects(parameters.getConfigurationSection("warning_effects"), durationWarning);
        ridingEffects = ConfigurationUtils.getPotionEffects(parameters.getConfigurationSection("riding_effects"), Integer.MAX_VALUE, true, false);

        if (parameters.contains("crash_into")) {
            String entityTypeName = parameters.getString("crash_into");
            try {
                crashEntityType = Class.forName("org.bukkit.entity." + entityTypeName);
            } catch (Throwable ex) {
                spell.getController().getLogger().warning("Unknown entity type in crash_into: " + entityTypeName);
                crashEntityType = null;
            }
        }
    }

    @Override
    public void reset(CastContext context)
    {
        super.reset(context);
        if (mount != null) {
            mount.eject();
        }
        mount = null;
        warningEffectsApplied = false;
        nextSoundPlay = 0;
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters)
    {
        super.prepare(context, parameters);
        moveDistance = parameters.getDouble("steer_speed", 0);
        startSpeed = parameters.getDouble("start_speed", 0);
        minSpeed = parameters.getDouble("min_speed", 0);
        maxSpeed = parameters.getDouble("max_speed", 0);
        maxAcceleration = parameters.getDouble("max_acceleration", 0);
        maxDeceleration = parameters.getDouble("max_deceleration", 0);
        liftoffThrust = parameters.getDouble("liftoff_thrust", 0);
        liftoffDuration = parameters.getInt("liftoff_duration", 0);
        crashDistance = parameters.getDouble("crash_distance", 0);
        crashSpeed = parameters.getDouble("crash_speed", 0);
        maxHeight = parameters.getInt("max_height", 0);
        maxHeightAboveGround = parameters.getInt("max_height_above_ground", -1);
        heightCheckRadius = parameters.getInt("height_check_radius", 0);
        gravity = parameters.getDouble("gravity", 0);
        terminalVelocity = parameters.getDouble("terminal_velocity", 0);
        maxAscend = parameters.getInt("max_ascend", 0);
        duration = parameters.getInt("duration", 0);
        durationWarning = parameters.getInt("duration_warning", 0);
        pitchOffset = parameters.getDouble("pitch_offset", 0);
        yawOffset = parameters.getDouble("yaw_offset", 0);
        noTarget = parameters.getBoolean("mount_untargetable", false);
        noTargetPlayer = parameters.getBoolean("rider_untargetable", false);
        controllable = parameters.getBoolean("controllable", false);
        pitchControllable = parameters.getBoolean("pitch_controllable", true);
        airControllable = parameters.getBoolean("air_controllable", true);
        strafeControllable = parameters.getDouble("strafe_controllable", 0.0);
        steerControllable = parameters.getDouble("steer_controllable", 0.0);
        jumpControllable = parameters.getDouble("jump_controllable", 0.0);
        jumpVelocity = parameters.getDouble("jump_velocity", 0.0);
        braking = parameters.getDouble("braking", 0.0);
        crashEntityDistance = parameters.getDouble("crash_entity_distance", 2.0);
        crashVelocityYOffset = parameters.getDouble("crash_velocity_y_offset", 0.0);
        crashVelocity = parameters.getDouble("crash_velocity", 0.0);
        crashDamage = parameters.getDouble("crash_damage", 0.0);
        crashVehicleDamage = parameters.getDouble("crash_vehicle_damage", 0.0);
        crashEntityVehicleDamage = parameters.getDouble("crash_entity_vehicle_damage", 0.0);
        crashEntityDamage = parameters.getDouble("crash_entity_damage", 0.0);
        crashBraking = parameters.getDouble("crash_braking", 0.0);
        crashEntityFOV = parameters.getDouble("crash_entity_fov", 0.3);
        crashDismountSpeed = parameters.getDouble("crash_dismount_speed", 0.0);
        fallProtection = parameters.getInt("fall_protection", 0);
        exemptionDuration = parameters.getInt("exemption_duration", 0);
        boostAttribute = parameters.getString("boost_attribute");
        if (parameters.contains("direction_y")) {
            yDirection = parameters.getDouble("direction_y");
        } else {
            yDirection = null;
        }
        isPassenger = parameters.getBoolean("passenger", false);

        sound = null;
        String soundKey = parameters.getString("sound");
        if (soundKey != null && !soundKey.isEmpty()) {
            sound = new SoundEffect(soundKey);
        }
        soundInterval =  parameters.getInt("sound_interval", 1000);
        soundMaxVolume = (float)parameters.getDouble("sound_volume", 1);
        soundMaxPitch = (float)parameters.getDouble("sound_pitch", 1);
        soundMinVolume = (float)parameters.getDouble("sound_volume", 1);
        soundMinPitch = (float)parameters.getDouble("sound_pitch", 1);
        soundMaxVolume = (float)parameters.getDouble("sound_max_volume", soundMaxVolume);
        soundMaxPitch = (float)parameters.getDouble("sound_max_pitch", soundMaxPitch);
        soundMinVolume = (float)parameters.getDouble("sound_min_volume", soundMinVolume);
        soundMinPitch = (float)parameters.getDouble("sound_min_pitch", soundMinPitch);
        String jumpCast = parameters.getString("jump_cast", "");
        if (!jumpCast.isEmpty()) {
            String[] pieces = StringUtils.split(jumpCast, " ");
            jumpSpell = context.getMage().getSpell(pieces[0]);
            jumpSpellParameters = Arrays.copyOfRange(pieces, 1, pieces.length);
            if (jumpSpell == null) {
                context.getLogger().warning("Unknown jump_cast spell: " + jumpCast);
            }
        }
    }

    protected void remount(CastContext context) {
    }

    @Override
    public SpellResult perform(CastContext context) {
        if (mount == null) {
            return mount(context);
        }
        Entity mounted = context.getEntity();
        if (mounted == null)
        {
            return SpellResult.ENTITY_REQUIRED;
        }
        Entity currentMount = isPassenger ? DeprecatedUtils.getPassenger(mounted) : mounted.getVehicle();
        if (currentMount == null) {
            return SpellResult.CAST;
        }
        if (duration > 0) {
            long flightTime = System.currentTimeMillis() - liftoffTime;
            if (flightTime > duration) {
                return SpellResult.CAST;
            }
        }
        if (!mount.isValid() || mount != currentMount) {
            remount(context);
            if (mount == null) {
                return SpellResult.CAST;
            }
            if (isPassenger) {
                DeprecatedUtils.setPassenger(mounted, mount);
            } else {
                DeprecatedUtils.setPassenger(mount, mounted);
            }
        }

        // Play sound effects
        if (sound != null && nextSoundPlay < System.currentTimeMillis()) {
            nextSoundPlay = System.currentTimeMillis() + soundInterval;

            double speedRatio = 1;
            if (speed > 0) {
                double minForwardSpeed = Math.max(0, minSpeed);
                speedRatio = minSpeed >= maxSpeed ? 1 : (speed - minForwardSpeed) / (maxSpeed - minForwardSpeed);
            } else if (minSpeed < 0) {
                double maxBackwardSpeed = Math.max(Math.abs(minSpeed), maxSpeed);
                double backwardSpeed = Math.abs(speed);
                speedRatio = backwardSpeed / maxBackwardSpeed;
            }
            sound.setPitch((float)((soundMaxPitch - soundMinPitch) * speedRatio) + soundMinPitch);
            sound.setVolume((float)((soundMaxVolume - soundMinVolume) * speedRatio) + soundMinVolume);
            sound.play(context.getPlugin(), context.getController().getLogger(), mounted);
        }

        // Check for crashing
        if (crashDistance > 0 && Math.abs(speed) >= crashSpeed && System.currentTimeMillis() > liftoffTime + liftoffDuration)
        {
            Vector threshold = direction.clone().multiply(speed * crashDistance);
            if (checkForCrash(context, mounted.getLocation(), threshold)) {
                if (crash(context)) {
                    return SpellResult.CAST;
                }
            }
        }
        if (!context.isPassthrough(mounted.getLocation().getBlock())) {
            if (crash(context)) {
                return SpellResult.CAST;
            }
        }
        if (crashEntityType != null && speed > 0 && crashEntityDistance > 0 && maxSpeed > 0) {
            List<Entity> nearby = mounted.getNearbyEntities(crashEntityDistance, crashEntityDistance, crashEntityDistance);
            Vector crashDirection = direction.clone();
            if (crashVelocityYOffset > 0) {
                crashDirection.setY(crashDirection.getY() + crashVelocityYOffset).normalize();
            }
            Vector velocity = crashDirection.multiply(crashVelocity * speed / maxSpeed);
            for (Entity entity : nearby) {
                if (entity == mounted || entity == mount || !entity.isValid() || !context.canTarget(entity, crashEntityType)) continue;

                Vector targetDirection = entity.getLocation().subtract(mounted.getLocation()).toVector();
                double angle = targetDirection.angle(direction);
                if (angle > crashEntityFOV) continue;
                if (crashEntityDamage > 0 && entity instanceof Damageable) {
                    Damageable damageable = (Damageable)entity;
                    double crashDamage = maxSpeed > 0 ? crashEntityDamage * speed / maxSpeed : crashEntityDamage;
                    CompatibilityUtils.damage(damageable, crashDamage, mounted);
                }
                SafetyUtils.setVelocity(entity, velocity);
                speed = Math.max(0, speed - crashBraking);
                if (mount instanceof Damageable && crashEntityVehicleDamage > 0) {
                    double crashDamage = maxSpeed > 0 ? crashEntityVehicleDamage * speed / maxSpeed : crashEntityVehicleDamage;
                    CompatibilityUtils.damage((Damageable)mount, crashDamage, mounted);
                }
                context.playEffects("crash_entity", 1.0f, null, mounted, null, entity);
            }
        }

        if (exemptionDuration > 0 && mounted instanceof Player) {
            context.getController().addFlightExemption((Player)mounted, exemptionDuration);
        }

        if (!isInAir || airControllable) {
            adjustHeading(context);
        }
        if (System.currentTimeMillis() > liftoffTime + liftoffDuration) {
            applyThrust(context);
        }

        if (fallProtection > 0) {
            context.getMage().enableFallProtection(fallProtection, Integer.MAX_VALUE, context.getSpell());
        }

        if (jumpSpell != null && context.getMage().isVehicleJumping()) {
            jumpSpell.cast(jumpSpellParameters);
        }

        return SpellResult.PENDING;
    }

    protected void adjustHeading(CastContext context) {
        targetLocation = context.getEntity().getLocation();

        // Use strafe steering controls
        if (steerControllable != 0) {
            targetLocation.setDirection(direction);
            double strafeDirection = context.getMage().getVehicleStrafeDirection();
            if (strafeDirection != 0) {
                strafeDirection = -strafeDirection * steerControllable;
                targetLocation.setYaw(targetLocation.getYaw() + (float)strafeDirection);
            }
        }
        Vector targetDirection = targetLocation.getDirection();

        if (jumpControllable != 0 && context.getMage().isVehicleJumping()) {
            targetDirection.setY(jumpControllable);
        }

        if (maxAscend > 0) {
            targetDirection.setY(0);
            Location checkLocation = targetLocation;
            Block checkBlock = checkLocation.getBlock();
            Block oneDown = checkBlock.getRelative(BlockFace.DOWN);
            if (context.isPassthrough(checkBlock) && context.isPassthrough(oneDown)) {
                Block twoDown = oneDown.getRelative(BlockFace.DOWN);
                if (context.isPassthrough(twoDown)) {
                    if (isAscending) {
                        checkBlock = twoDown;
                        checkLocation.setY(checkLocation.getY() - 2);
                    }
                } else {
                    checkBlock = oneDown;
                    checkLocation.setY(checkLocation.getY() - 1);
                }
            }
            isAscending = false;
            if (!context.isPassthrough(checkBlock) && context.isPassthrough(checkBlock.getRelative(BlockFace.UP))) {
                targetDirection.setY(10);
                isAscending = true;
            } else {
                Vector ahead = targetDirection.clone().multiply(0.75);
                Location checkSlope = checkLocation.clone().add(ahead);
                if (checkSlope.equals(checkLocation)) {
                    checkSlope = checkSlope.add(ahead);
                }
                checkBlock = checkSlope.getBlock();
                if (!context.isPassthrough(checkBlock) && context.isPassthrough(checkBlock.getRelative(BlockFace.UP))) {
                    targetDirection.setY(10);
                    isAscending = true;
                }
            }
        }

        if (moveDistance == 0) {
            direction = targetDirection;
        } else {
            double moveDistanceSquared = moveDistance * moveDistance;
            double distanceSquared = direction.distanceSquared(targetDirection);
            if (distanceSquared <= moveDistanceSquared) {
                direction = targetDirection;
            } else {
                targetDirection = targetDirection.subtract(direction).normalize().multiply(moveDistance);
                direction.add(targetDirection).normalize();
            }
        }

        targetLocation.setDirection(direction);
        CompatibilityUtils.setYawPitch(mount, targetLocation.getYaw() + (float)yawOffset, targetLocation.getPitch());
    }

    protected void applyThrust(CastContext context) {
        Entity mountedEntity = context.getEntity();
        if (duration > 0) {
            long flightTime = System.currentTimeMillis() - liftoffTime;
            if (!warningEffectsApplied && warningEffects != null && mountedEntity instanceof LivingEntity && durationWarning > 0 && flightTime > duration - durationWarning) {
                CompatibilityUtils.applyPotionEffects((LivingEntity)mountedEntity, warningEffects);
                warningEffectsApplied = true;
            }

            if (flightTime > duration) {
                return;
            }
        }

        double minBrakingSpeed = Math.max(0, minSpeed);

        // Adjust speed
        if (pitchControllable) {
            if (direction.getY() < 0 && maxAcceleration > 0) {
                speed = speed - direction.getY() * maxAcceleration;
                if (maxSpeed > 0 && speed > maxSpeed) {
                    speed = maxSpeed;
                }
            } else if (direction.getY() > 0 && maxDeceleration > 0) {
                speed = speed - direction.getY() * maxDeceleration;
                speed = Math.max(minBrakingSpeed, speed);
            }
        }

        if (jumpVelocity != 0 && context.getMage().isVehicleJumping()) {
            speed += jumpVelocity;
        }

        if (boostAttribute != null) {
            Double attribute = context.getMage().getAttribute(boostAttribute);
            if (attribute != null) {
                speed += attribute;
            }
        }

        if (controllable) {
            double direction = context.getMage().getVehicleMovementDirection();
            if (direction > 0) {
                speed = speed + maxAcceleration;
                if (maxSpeed > 0 && speed > maxSpeed) {
                    speed = maxSpeed;
                }
            } else if (direction < 0) {
                speed = speed - maxDeceleration;
                speed = Math.max(minSpeed, speed);
            } else {
                speed = speed - braking;
                speed = Math.max(minBrakingSpeed, speed);
            }
        }

        // Apply pitch offset
        if (yDirection != null) {
            direction.setY(yDirection).normalize();
        }
        if (pitchOffset != 0) {
            direction.setY(direction.getY() + pitchOffset).normalize();
        }

        // Check for max height
        double blocksAbove = 0;
        boolean first = true;
        Location currentLocation = mountedEntity.getLocation();
        if (maxHeight > 0 && currentLocation.getY() >= maxHeight) {
            blocksAbove = currentLocation.getY() - maxHeight + 1;
        } else if (maxHeightAboveGround >= 0) {
            for (int x = -heightCheckRadius; x <= heightCheckRadius && (first || blocksAbove > 0); x++) {
                for (int z = -heightCheckRadius; z <= heightCheckRadius && (first || blocksAbove > 0); z++) {
                    int thisBlocksAbove = 0;
                    Block block = currentLocation.getBlock().getRelative(x, 0, z);
                    while (thisBlocksAbove < maxHeightAboveGround + 5 && context.isPassthrough(block))
                    {
                        block = block.getRelative(BlockFace.DOWN);
                        thisBlocksAbove++;
                    }
                    if (first) {
                        first = false;
                        blocksAbove = thisBlocksAbove;
                    } else {
                        blocksAbove = Math.min(thisBlocksAbove, blocksAbove);
                    }
                }
            }
            blocksAbove = blocksAbove - maxHeightAboveGround - 1;
        }

        if (isAscending) {
            blocksAbove -= (maxAscend + 1);
        }

        int multiplier = speed < 0 ? -1 : 1;
        isInAir = blocksAbove > 1;
        if (blocksAbove > 0 && multiplier * direction.getY() >= 0) {
            if (blocksAbove > 1) {
                direction.setY(multiplier * -blocksAbove / 5).normalize();
            } else {
                direction.setY(0).normalize();
            }
        }

        // Apply strafe movement
        Vector velocity = direction.clone();
        if (strafeControllable != 0) {
            double strafeDirection = context.getMage().getVehicleStrafeDirection();
            if (strafeDirection != 0) {
                Vector strafeVector = new Vector(0, 0, -strafeDirection * strafeControllable);
                strafeVector = VectorUtils.rotateVector(strafeVector, mountedEntity.getLocation());
                velocity.add(strafeVector).normalize();
            }
        }
        // Apply thrust or gravity as appropriate
        if (isInAir && gravity > 0) {
            // This updates every tick, and also velocity is in ticks so we have to
            // convert to seconds *twice*
            double gravitySpeed = Math.abs(gravityVelocity.getY()) + gravity / 20 / 20;
            if (terminalVelocity > 0) {
                double terminalSpeed = terminalVelocity / 20;
                if (gravitySpeed > terminalSpeed) {
                    gravitySpeed = terminalSpeed;
                }
            }
            gravityVelocity.setY(-gravitySpeed);
            Vector totalVelocity = gravityVelocity;
            if (speed != 0 && airControllable) {
                totalVelocity = totalVelocity.clone();
                totalVelocity.add(velocity.multiply(speed));
            }
            SafetyUtils.setVelocity(getMount(context), totalVelocity);
        } else {
            gravityVelocity.setY(0);
            if (speed != 0) {
                velocity = velocity.multiply(speed);
                SafetyUtils.setVelocity(getMount(context), velocity);
            }
        }
    }

    @Nullable
    protected Entity getMount(CastContext context) {
        return isPassenger ? context.getEntity() : mount;
    }

    protected SpellResult mount(CastContext context) {
        Entity entity = context.getEntity();
        if (entity == null)
        {
            return SpellResult.NO_TARGET;
        }
        mount = context.getTargetEntity();
        if (mount == null)
        {
            return SpellResult.NO_TARGET;
        }
        if (noTarget) {
            EntityMetadataUtils.instance().setBoolean(mount, MagicMetaKeys.NO_TARGET, true);
        }
        if (isPassenger) {
            DeprecatedUtils.setPassenger(entity, mount);
        } else {
            DeprecatedUtils.setPassenger(mount, entity);
        }
        direction = mount.getLocation().getDirection();
        adjustHeading(context);

        liftoffTime = System.currentTimeMillis();
        speed = startSpeed;
        if (liftoffThrust > 0) {
            SafetyUtils.setVelocity(getMount(context), new Vector(0, liftoffThrust, 0));
        }
        if (sound != null) {
            nextSoundPlay = System.currentTimeMillis();
        }
        if (noTargetPlayer) {
            EntityMetadataUtils.instance().setBoolean(entity, MagicMetaKeys.NO_TARGET, true);
        }
        if (ridingEffects != null && entity instanceof LivingEntity) {
            CompatibilityUtils.applyPotionEffects((LivingEntity)entity, ridingEffects);
        }

        return SpellResult.PENDING;
    }

    @Override
    public void finish(CastContext context) {
        if (noTarget && mount != null) {
            EntityMetadataUtils.instance().setBoolean(mount, MagicMetaKeys.NO_TARGET, false);
        }
        if (mount != null) {
            mount.eject();
            mount = null;
        }
        Entity mountedEntity = context.getEntity();
        if (noTargetPlayer && mountedEntity != null) {
            EntityMetadataUtils.instance().setBoolean(mountedEntity, MagicMetaKeys.NO_TARGET, false);
        }
        if (warningEffectsApplied && warningEffects != null && mountedEntity != null && mountedEntity instanceof LivingEntity) {
            for (PotionEffect effect : warningEffects) {
                ((LivingEntity)mountedEntity).removePotionEffect(effect.getType());
            }
        }
        if (ridingEffects != null && mountedEntity != null && mountedEntity instanceof LivingEntity) {
            for (PotionEffect effect : ridingEffects) {
                ((LivingEntity)mountedEntity).removePotionEffect(effect.getType());
            }
        }
    }

    protected boolean crash(CastContext context)
    {
        context.sendMessageKey("crash");
        context.playEffects("crash");
        Entity mountedEntity = context.getEntity();
        if (crashDamage > 0) {
            double damage = maxSpeed > 0 ? crashDamage * speed / maxSpeed : crashDamage;
            if (mountedEntity.isValid() && mountedEntity instanceof Damageable) {
                CompatibilityUtils.damage((Damageable)mountedEntity, damage, mount);
            }
        }
        if (crashVehicleDamage > 0) {
            double damage = maxSpeed > 0 ? crashVehicleDamage * speed / maxSpeed : crashVehicleDamage;
            if (mount != null && mount.isValid() && mount instanceof Damageable) {
                CompatibilityUtils.damage((Damageable)mount, damage, mountedEntity);
            }
        }
        boolean dismount = (speed >= crashDismountSpeed);
        if (dismount) {
            if (crashVelocity > 0 && maxSpeed > 0) {
                mount.eject();
                Vector crashDirection = direction.clone();
                if (crashVelocityYOffset > 0) {
                    crashDirection.setY(crashDirection.getY() + crashVelocityYOffset).normalize();
                }
                Vector velocity = crashDirection.multiply(crashVelocity * speed / maxSpeed);
                SafetyUtils.setVelocity(mountedEntity, velocity);
            }
            if (crashEffects != null && mountedEntity != null && crashEffects.size() > 0 && mountedEntity instanceof LivingEntity) {
                CompatibilityUtils.applyPotionEffects((LivingEntity)mountedEntity, crashEffects);
            }
            warningEffectsApplied = false;
        }
        speed = 0;
        return dismount;
    }

    protected boolean checkForCrash(CastContext context, Location source, Vector threshold)
    {
        if (maxAscend > 0) {
            source.setY(source.getY() + maxAscend);
        }
        Block facingBlock = source.getBlock();
        Block targetBlock = source.add(threshold).getBlock();

        if (!targetBlock.equals(facingBlock) && !context.isPassthrough(targetBlock)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isUndoable()
    {
        return false;
    }

    @Override
    public void getParameterNames(Spell spell, Collection<String> parameters)
    {
        super.getParameterNames(spell, parameters);
        parameters.add("steer_speed");
        parameters.add("liftoff_duration");
        parameters.add("liftoff_thrust");
        parameters.add("crash_distance");
        parameters.add("max_height");
        parameters.add("max_height_above_ground");
        parameters.add("duration");
        parameters.add("duration_warning");
        parameters.add("start_speed");
        parameters.add("min_speed");
        parameters.add("max_speed");
        parameters.add("max_acceleration");
        parameters.add("max_deceleration");
        parameters.add("pitch_offset");
        parameters.add("yaw_offset");
        parameters.add("braking");
        parameters.add("controllable");
        parameters.add("strafe_controllable");
        parameters.add("pitch_controllable");
        parameters.add("jump_controllable");
        parameters.add("gravity");
        parameters.add("air_controllable");
        parameters.add("height_check_radius");
    }

    @Override
    public void getParameterOptions(Spell spell, String parameterKey, Collection<String> examples)
    {
        if (parameterKey.equals("crash_distance")
                || parameterKey.equals("max_height")
                || parameterKey.equals("height_check_radius")
                || parameterKey.equals("max_height_above_ground")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_SIZES));
        } else if (parameterKey.equals("steer_speed")
                || parameterKey.equals("start_speed")
                || parameterKey.equals("min_speed")
                || parameterKey.equals("max_speed")
                || parameterKey.equals("max_acceleration")
                || parameterKey.equals("max_deceleration")
                || parameterKey.equals("braking")
                || parameterKey.equals("pitch_offset")
                || parameterKey.equals("yaw_offset")
                || parameterKey.equals("gravity")
                || parameterKey.equals("liftoff_thrust")
                || parameterKey.equals("jump_controllable")
                || parameterKey.equals("strafe_controllable")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_VECTOR_COMPONENTS));
        } else if (parameterKey.equals("liftoff_duration")
                || parameterKey.equals("duration")
                || parameterKey.equals("duration_warning")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_DURATIONS));
        } else if (parameterKey.equals("air_controllable")
                || parameterKey.equals("controllable")
                || parameterKey.equals("pitch_controllable")) {
            examples.addAll(Arrays.asList(BaseSpell.EXAMPLE_BOOLEANS));
        } else {
            super.getParameterOptions(spell, parameterKey, examples);
        }
    }
}

package com.elmakers.mine.bukkit.magic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.elmakers.mine.bukkit.api.magic.MagicPropertyType;
import com.elmakers.mine.bukkit.api.magic.Trigger;
import com.elmakers.mine.bukkit.api.spell.CooldownReducer;
import com.elmakers.mine.bukkit.api.spell.CostReducer;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.spell.TriggeredSpell;
import com.elmakers.mine.bukkit.wand.Wand;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class BaseMageModifier extends ParentedProperties implements CostReducer, CooldownReducer {
    public static final ImmutableSet<String> PROPERTY_KEYS = new ImmutableSet.Builder<String>()
        .addAll(BaseMagicProperties.PROPERTY_KEYS)
        .add("craftable", "useable", "health_scale", "entity_attributes", "triggers").build();

    private List<EntityAttributeModifier> attributeModifiers;
    private boolean checkedAttributes = false;
    protected @Nullable
    Multimap<String, CustomTrigger> triggers;
    protected final Multimap<String, TriggeredSpell> spellTriggers = ArrayListMultimap.create();

    protected final Mage mage;

    public BaseMageModifier(Mage mage, MagicPropertyType type, @Nullable TemplateProperties template) {
        super(type, mage.getController(), template);
        this.mage = mage;
    }

    @Override
    public Mage getMage() {
        return mage;
    }

    @Override
    public boolean isPlayer() {
        return mage.isPlayer();
    }

    @Nullable
    @Override
    public Player getPlayer() {
        return mage.getPlayer();
    }

    @Override
    public void updated() {
        super.updated();
        Mage mage = getMage();
        if (mage == null) return;
        updateMaxMana(mage);
        Wand activeWand = mage.getActiveWand();
        if (activeWand != null) {
            activeWand.updated();
        }
        if (!isLocked()) {
            deactivate();
            activate();
        }
        mage.updatePassiveEffects();
    }

    public void activate() {
        activateAttributes();
        trigger("join");
    }

    protected void activateAttributes() {
        double healthScale = getDouble("health_scale");
        if (healthScale > 0) {
            Player player = mage.getPlayer();
            if (player != null) {
                player.setHealthScale(healthScale);
            }
        }

        Collection<EntityAttributeModifier> modifiers = getAttributeModifiers();
        if (modifiers == null) return;
        LivingEntity entity = mage.getLivingEntity();
        if (entity == null) return;

        for (EntityAttributeModifier modifier : modifiers) {
            AttributeInstance attribute = entity.getAttribute(modifier.attribute);

            if (modifier.modifier != null) {
                if (!checkedAttributes) {
                    // Only do this once, it's really here to clean up attributes that may have gotten stuck on server crash
                    Collection<AttributeModifier> existingModifiers = attribute.getModifiers();
                    for (AttributeModifier existing : existingModifiers) {
                        if (existing.getName().equalsIgnoreCase(modifier.modifier.getName())) {
                            mage.getController().getLogger().warning("Removed duplicate attribute modifier " + modifier.modifier.getName() + ", was this leftover from a server crash?");
                            attribute.removeModifier(existing);
                            break;
                        }
                    }
                }
                try {
                    attribute.addModifier(modifier.modifier);
                } catch (Exception ex) {
                    controller.getLogger().log(Level.WARNING, "Error adding vanilla attribute modifier: " + modifier.modifier.getName() + " from class/modifier " + getKey(), ex);
                }
            }

            if (modifier.base != null) {
                modifier.previous = attribute.getBaseValue();
                attribute.setBaseValue(modifier.base);
            }
        }

        checkedAttributes = true;
    }

    public void deactivate() {
        deactivateAttributes();
        cancelTrigger("join");
    }

    protected void deactivateAttributes() {
        double healthScale = getDouble("health_scale");
        if (healthScale > 0) {
            Player player = mage.getPlayer();
            if (player != null) {
                player.setHealthScaled(false);
            }
        }

        if (attributeModifiers == null) return;
        LivingEntity entity = mage.getLivingEntity();
        if (entity == null) return;

        // Remove in reverse-order in case a base attribute was changed twice
        ListIterator<EntityAttributeModifier> it = attributeModifiers.listIterator(attributeModifiers.size());
        while (it.hasPrevious()) {
            EntityAttributeModifier modifier = it.previous();
            AttributeInstance attribute = entity.getAttribute(modifier.attribute);
            if (modifier.modifier != null) {
                attribute.removeModifier(modifier.modifier);
            }
            if (modifier.previous != null) {
                attribute.setBaseValue(modifier.previous);
            }
        }
        attributeModifiers = null;
    }

    @Nullable
    public Collection<EntityAttributeModifier> getAttributeModifiers() {
        if (attributeModifiers != null) {
            return attributeModifiers;
        }

        ConfigurationSection config = getConfigurationSection("entity_attributes");
        if (config == null) return null;
        Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) return null;
        attributeModifiers = new ArrayList<>();
        for (String key : keys) {
            String name = "mage_" + getKey() + "_" + key;
            double value;
            Double base = null;
            String attributeKey = key;
            AttributeModifier.Operation operation = AttributeModifier.Operation.ADD_NUMBER;
            if (config.isConfigurationSection(key)) {
                ConfigurationSection modifierConfig = config.getConfigurationSection(key);
                name = modifierConfig.getString("name", name);
                attributeKey = modifierConfig.getString("attribute", attributeKey);
                value = modifierConfig.getDouble("value");
                String operationType = modifierConfig.getString("operation");
                if (operationType.equalsIgnoreCase("base")) {
                    base = value;
                } else if (operationType != null && !operationType.isEmpty()) {
                    try {
                        operation = AttributeModifier.Operation.valueOf(operationType.toUpperCase());
                    } catch (Exception ex) {
                        controller.getLogger().warning("Invalid operation " + operationType + " on entity_attributes." + key + " in mage class " + getKey());
                    }
                }
            } else {
                value = config.getDouble(key);
            }
            Attribute attribute = null;
            try {
                attribute = Attribute.valueOf(attributeKey.toUpperCase());
            } catch (Exception ex) {
                controller.getLogger().warning("Invalid attribute " + attributeKey + " on entity_attributes." + key + " in mage class " + getKey());
            }
            if (attribute != null) {
                if (base != null) {
                    attributeModifiers.add(new EntityAttributeModifier(attribute, base));
                } else {
                    AttributeModifier modifier = new AttributeModifier(name, value, operation);
                    attributeModifiers.add(new EntityAttributeModifier(attribute, modifier));
                }
            }
        }

        return attributeModifiers;
    }

    protected void cancelTrigger(String triggerType) {
        Collection<TriggeredSpell> triggers = getTriggers(triggerType);
        if (triggers != null) {
            for (TriggeredSpell triggered : triggers) {
                mage.cancelPending(triggered.getSpellKey());
            }
        }

        Collection<CustomTrigger> customTriggers = this.triggers == null ? null : this.triggers.get(triggerType);
        if (customTriggers != null) {
            for (CustomTrigger trigger : customTriggers) {
                trigger.cancel(mage);
            }
        }
    }

    public void trigger(String triggerType) {
        Collection<TriggeredSpell> triggers = getTriggers(triggerType);
        if (triggers != null) {
            for (TriggeredSpell triggered : triggers) {
                if (triggered.getTrigger().isValid(mage)) {
                    Spell spell = mage.getSpell(triggered.getSpellKey());
                    if (spell != null && spell.isEnabled()) {
                        spell.cast();
                        triggered.getTrigger().triggered();
                    }
                }
            }
        }

        Collection<CustomTrigger> customTriggers = this.triggers == null ? null : this.triggers.get(triggerType);
        if (customTriggers != null) {
            for (CustomTrigger trigger : customTriggers) {
                trigger.execute(mage);
            }
        }
    }

    @Nullable
    protected Collection<TriggeredSpell> getTriggers(String triggerType) {
        return spellTriggers.get(triggerType);
    }

    @Nullable
    @Override
    public BaseMagicConfigurable getStorage(MagicPropertyType propertyType) {
        switch (propertyType) {
            case SUBCLASS: return this;
            case CLASS: return getRoot();
            case MAGE: return mage == null ? null : mage.getProperties();
            case WAND: return mage == null ? null : mage.getActiveWand();
            default: return null;
        }
    }

    @Override
    public float getCostReduction() {
        float costReduction = getFloat("cost_reduction");
        if (mage != null) {
            float reduction = mage.getCostReduction();
            return stackPassiveProperty(reduction, costReduction);
        }
        return costReduction;
    }

    @Override
    public float getCooldownReduction() {
        float cooldownReduction = getFloat("cooldown_reduction");
        if (mage != null) {
            float reduction = mage.getCooldownReduction();
            return stackPassiveProperty(reduction, cooldownReduction);
        }
        return cooldownReduction;
    }

    @Override
    public boolean isCooldownFree() {
        return getFloat("cooldown_reduction") > 1;
    }

    @Override
    public float getConsumeReduction() {
        float consumeReduction = getFloat("consume_reduction");
        if (mage != null) {
            float reduction = mage.getConsumeReduction();
            return stackPassiveProperty(reduction, consumeReduction);
        }
        return consumeReduction;
    }

    @Override
    public float getCostScale() {
        return 1.0f;
    }

    protected void takeItems() {
        List<String> classItems = getStringList("gave_items");
        if (classItems != null) {
            for (String classItemKey : classItems) {
                ItemStack item = controller.createItem(classItemKey);
                if (item == null) {
                    // We already nagged about this on load...
                    continue;
                }

                mage.removeItem(item, true);
            }
            setProperty("gave_items", null);
        }
    }

    protected void giveItems(String key) {
        List<String> classItems = getStringList(key);
        if (classItems != null) {
            List<String> gaveItems = new ArrayList<>();
            for (String classItemKey : classItems) {
                ItemStack item = controller.createItem(classItemKey);
                if (item == null) {
                    controller.getLogger().warning("Invalid modifier item in " + getKey() + ": " + classItemKey);
                    continue;
                }

                if (!mage.hasItem(item)) {
                    gaveItems.add(classItemKey);
                    String wandKey = controller.getWandKey(item);
                    if (wandKey != null) {
                        Wand wand = mage.getBoundWand(wandKey);
                        if (wand != null) {
                            mage.giveItem(wand.getItem());
                            continue;
                        }
                    }

                    mage.giveItem(item);
                }
            }
            if (!gaveItems.isEmpty()) {
                setProperty("gave_items", gaveItems);
            }
        }
    }

    @Override
    public void loadProperties() {
        super.loadProperties();

        ConfigurationSection triggerConfig = getConfigurationSection("triggers");
        Set<String> triggerKeys = triggerConfig == null ? null : triggerConfig.getKeys(false);
        if (triggerKeys != null) {
            triggers = ArrayListMultimap.create();
            for (String triggerKey : triggerKeys) {
                ConfigurationSection config = triggerConfig.getConfigurationSection(triggerKey);
                if (triggerConfig == null) {
                    controller.getLogger().warning("Invalid trigger specified in " + getKey() + ": " + triggerKey);
                    continue;
                }
                CustomTrigger trigger = new CustomTrigger(controller, triggerKey, config);
                triggers.put(trigger.getTrigger(), trigger);
            }
        } else {
            triggers = null;
        }
        updateSpellTriggers();
    }

    @Override
    public boolean removeSpell(String spellKey) {
        boolean result = super.removeSpell(spellKey);
        if (result) {
            updateSpellTriggers();
        }
        return result;
    }

    @Override
    public boolean addSpell(String spellKey) {
        boolean result = super.addSpell(spellKey);
        if (result) {
            updateSpellTriggers();
        }
        return result;
    }

    public void updateSpellTriggers() {
        spellTriggers.clear();
        for (String spellKey : getSpells()) {
            Spell spell = getSpell(spellKey);
            if (spell == null) continue;
            Collection<Trigger> triggers = spell.getTriggers();
            if (triggers == null) continue;
            for (Trigger trigger : triggers) {
                spellTriggers.put(trigger.getTrigger(), new TriggeredSpell(spellKey, trigger));
            }
        }
    }

    public boolean canUse(String itemKey) {
        List<String> useable = getStringList("useable");
        if (useable == null) return false;
        for (String key : useable) {
            if (key.equalsIgnoreCase(itemKey)) {
                return true;
            }
        }
        return false;
    }

    public boolean canCraft(String recipeKey) {
        List<String> craftable = getStringList("craftable");
        if (craftable == null) return false;
        for (String key : craftable) {
            if (key.equalsIgnoreCase(recipeKey)) {
                return true;
            }
        }
        return false;
    }
}

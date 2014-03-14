package com.elmakers.mine.bukkit.plugins.magic.wand;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import com.elmakers.mine.bukkit.blocks.MaterialAndData;
import com.elmakers.mine.bukkit.blocks.MaterialBrush;
import com.elmakers.mine.bukkit.blocks.MaterialBrushData;
import com.elmakers.mine.bukkit.effects.EffectRing;
import com.elmakers.mine.bukkit.effects.ParticleType;
import com.elmakers.mine.bukkit.plugins.magic.BrushSpell;
import com.elmakers.mine.bukkit.plugins.magic.CastingCost;
import com.elmakers.mine.bukkit.plugins.magic.CostReducer;
import com.elmakers.mine.bukkit.plugins.magic.Mage;
import com.elmakers.mine.bukkit.plugins.magic.MagicController;
import com.elmakers.mine.bukkit.plugins.magic.Spell;
import com.elmakers.mine.bukkit.plugins.magic.WandMode;
import com.elmakers.mine.bukkit.utilities.InventoryUtils;
import com.elmakers.mine.bukkit.utilities.Messages;
import com.elmakers.mine.bukkit.utilities.borrowed.ConfigurationNode;

public class Wand implements CostReducer {
	public final static int INVENTORY_SIZE = 27;
	public final static int HOTBAR_SIZE = 9;
	public final static String[] PROPERTY_KEYS = {
		"active_spell", "active_material", "xp", "xp_regeneration", "xp_max", "health_regeneration", 
		"hunger_regeneration", "uses", 
		"cost_reduction", "cooldown_reduction", "power", "protection", "protection_physical", 
		"protection_projectiles", "protection_falling", "protection_fire", "protection_explosions", 
		"haste", "has_inventory", "modifiable", "effect_color", "effect_particle", "effect_particle_data",
		"effect_particle_count", "effect_bubbles", "materials", "spells", "mode", "icon"};
	
	private ItemStack item;
	private MagicController controller;
	private Mage mage;
	
	// Cached state
	private String id;
	private Inventory hotbar;
	private List<Inventory> inventories;
	
	private String activeSpell = "";
	private String activeMaterial = "";
	private String wandName = "";
	private String description = "";
	private String owner = "";
	private String template = "";
	
	private MaterialAndData icon = null;
	
	private float costReduction = 0;
	private float cooldownReduction = 0;
	private float damageReduction = 0;
	private float damageReductionPhysical = 0;
	private float damageReductionProjectiles = 0;
	private float damageReductionFalling = 0;
	private float damageReductionFire = 0;
	private float damageReductionExplosions = 0;
	private float power = 0;
	private boolean hasInventory = false;
	private boolean modifiable = true;
	private int uses = 0;
	private int xp = 0;
	
	private int xpRegeneration = 0;
	private int xpMax = 50;
	private int healthRegeneration = 0;
	private int hungerRegeneration = 0;
	
	private int effectColor = 0;
	private ParticleType effectParticle = null;
	private float effectParticleData = 0;
	private int effectParticleCount = 1;
	private int particleFrequency = 2;
	private int particleCounter = 0;
	private boolean effectBubbles = false;
	private EffectRing effectPlayer = null;
	
	private float defaultWalkSpeed = 0.2f;
	private float defaultFlySpeed = 0.1f;
	private float speedIncrease = 0;
	
	private int storedXpLevel = 0;
	private int storedXp = 0;
	private float storedXpProgress = 0;
	
	// Kinda of a hacky initialization optimization :\
	private boolean suspendSave = false;
	
	private static DecimalFormat floatFormat = new DecimalFormat("#.###");
	
	public static boolean displayManaAsBar = true;
	public static Material DefaultWandMaterial = Material.BLAZE_ROD;
	public static Material EnchantableWandMaterial = Material.WOOD_SWORD;
	
	// Wand configurations
	protected static Map<String, ConfigurationNode> wandTemplates = new HashMap<String, ConfigurationNode>();
	
	// Inventory functionality
	WandMode mode = null;
	int openInventoryPage = 0;
	boolean inventoryIsOpen = false;
	Inventory displayInventory = null;
	
	private Wand(ItemStack itemStack) {
		hotbar = InventoryUtils.createInventory(null, 9, "Wand");
		this.icon = new MaterialAndData(itemStack.getType(), (byte)itemStack.getDurability());
		inventories = new ArrayList<Inventory>();
		item = itemStack;
	}
	
	public Wand(MagicController spells) {
		this(spells, DefaultWandMaterial, (short)0);
	}
	
	public Wand(MagicController spells, Material icon, short iconData) {
		// This will make the Bukkit ItemStack into a real ItemStack with NBT data.
		this(InventoryUtils.getCopy(new ItemStack(icon, 1, iconData)));
		InventoryUtils.addGlow(item);
		this.controller = spells;
		id = UUID.randomUUID().toString();
		wandName = Messages.get("wand.default_name");
		updateName();
		saveState();
	}
	
	public Wand(MagicController spells, ItemStack item) {
		this(item);
		this.controller = spells;
		loadState();
	}
	
	public void unenchant() {
		item = new ItemStack(item.getType(), 1, (short)item.getDurability());
	}
	
	public void setIcon(Material material, byte data) {
		setIcon(material == null ? null : new MaterialAndData(material, data));
	}
	
	public void setIcon(MaterialAndData materialData) {
		icon = materialData;
		if (icon != null) {
			item.setType(icon.getMaterial());
			item.setDurability(icon.getData());
		}
	}

	public void setActiveSpell(String activeSpell) {
		this.activeSpell = activeSpell;
		updateName();
		updateInventory();
		saveState();
	}
	
	protected void activateBrush(String materialKey) {
		setActiveMaterial(materialKey);
		if (materialKey != null) {
			MaterialBrush brush = mage.getBrush();
			brush.activate(mage.getLocation(), materialKey);
		}
	}
	
	public void activateBrush(ItemStack itemStack) {
		if (!isBrush(itemStack)) return;
		activateBrush(getMaterialKey(itemStack));
	}
	
	protected void setActiveMaterial(String materialKey) {
		this.activeMaterial = materialKey;
		updateName();
		updateActiveMaterial();
		updateInventory();
		saveState();
	}
	
	public int getXpRegeneration() {
		return xpRegeneration;
	}

	public int getXpMax() {
		return xpMax;
	}
	
	public int getExperience() {
		return xp;
	}
	
	public void removeExperience(int amount) {
		xp = Math.max(0,  xp - amount);
		updateMana();
	}

	public int getHealthRegeneration() {
		return healthRegeneration;
	}

	public int getHungerRegeneration() {
		return hungerRegeneration;
	}
	
	public boolean isModifiable() {
		return modifiable;
	}
	
	public boolean usesMana() {
		return xpMax > 0 && xpRegeneration > 0 && getCostReduction() < 1;
	}

	public float getCooldownReduction() {
		return controller.getCooldownReduction() + cooldownReduction;
	}

	public float getCostReduction() {
		return controller.getCostReduction() + costReduction;
	}
	
	public void setCooldownReduction(float reduction) {
		cooldownReduction = reduction;
	}
	
	public boolean getHasInventory() {
		return hasInventory;
	}

	public float getPower() {
		return power;
	}

	public float getDamageReduction() {
		return damageReduction;
	}

	public float getDamageReductionPhysical() {
		return damageReductionPhysical;
	}
	
	public float getDamageReductionProjectiles() {
		return damageReductionProjectiles;
	}

	public float getDamageReductionFalling() {
		return damageReductionFalling;
	}

	public float getDamageReductionFire() {
		return damageReductionFire;
	}

	public float getDamageReductionExplosions() {
		return damageReductionExplosions;
	}

	public int getUses() {
		return uses;
	}
	
	public String getName() {
		return wandName;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public void setName(String name) {
		wandName = name;
		updateName();
	}
	
	public void setTemplate(String templateName) {
		this.template = templateName;
	}
	
	public String getTemplate() {
		return this.template;
	}
	
	protected void setDescription(String description) {
		this.description = description;
		updateLore();
	}
	
	protected void takeOwnership(Player player) {
		owner = player.getName();
	}
	
	public void takeOwnership(Player player, String name, boolean updateDescription) {
		setName(name);
		takeOwnership(player);
		if (updateDescription) {
			setDescription("");
		}
	}
	
	public ItemStack getItem() {
		return item;
	}
	
	protected List<Inventory> getAllInventories() {
		List<Inventory> allInventories = new ArrayList<Inventory>(inventories.size() + 1);
		allInventories.add(hotbar);
		allInventories.addAll(inventories);
		return allInventories;
	}
	
	public Set<String> getSpells() {
		return getSpells(false);
	}
	
	protected Set<String> getSpells(boolean includePositions) {
		Set<String> spellNames = new TreeSet<String>();
		List<Inventory> allInventories = getAllInventories();
		int index = 0;
		for (Inventory inventory : allInventories) {
			ItemStack[] items = inventory.getContents();
			for (int i = 0; i < items.length; i++) {
				if (items[i] != null && !isWand(items[i])) {
					if (isSpell(items[i])) {
						String spellName = getSpell(items[i]);
						if (includePositions) {
							spellName += "@" + index;
						}
						spellNames.add(spellName);
					}
				}	
				index++;
			}
		}
		return spellNames;
	}
	
	protected String getSpellString() {
		return StringUtils.join(getSpells(true), "|");
	}

	public Set<String> getMaterialKeys() {
		return getMaterialKeys(false);
	}
	
	protected Set<String> getMaterialKeys(boolean includePositions) {
		Set<String> materialNames = new TreeSet<String>();
		List<Inventory> allInventories = new ArrayList<Inventory>(inventories.size() + 1);
		allInventories.add(hotbar);
		allInventories.addAll(inventories);
		Integer index = 0;
		for (Inventory inventory : allInventories) {
			ItemStack[] items = inventory.getContents();
			for (int i = 0; i < items.length; i++) {
				String materialKey = getMaterialKey(items[i], includePositions ? index : null);
				if (materialKey != null) {
					materialNames.add(materialKey);
				}
				index++;
			}
		}
		return materialNames;	
	}
	
	protected String getMaterialString() {
		return StringUtils.join(getMaterialKeys(true), "|");		
	}
	
	protected Integer parseSlot(String[] pieces) {
		Integer slot = null;
		if (pieces.length > 0) {
			try {
				slot = Integer.parseInt(pieces[1]);
			} catch (Exception ex) {
				slot = null;
			}
			if (slot != null && slot < 0) {
				slot = null;
			}
		}
		return slot;
	}
	
	protected void addToInventory(ItemStack itemStack) {
		// Set the wand item
		WandMode wandMode = getMode();
		Integer selectedItem = null;
		if (wandMode == WandMode.INVENTORY)  {
			if (mage != null && mage.getPlayer() != null) {
				selectedItem = mage.getPlayer().getInventory().getHeldItemSlot();
				hotbar.setItem(selectedItem, item);
			}
		}
		
		List<Inventory> checkInventories = wandMode == WandMode.INVENTORY ? getAllInventories() : inventories;
		boolean added = false;
		
		for (Inventory inventory : checkInventories) {
			HashMap<Integer, ItemStack> returned = inventory.addItem(itemStack);
			if (returned.size() == 0) {
				added = true;
				break;
			}
		}
		if (!added) {
			Inventory newInventory = InventoryUtils.createInventory(null, INVENTORY_SIZE, "Wand");
			newInventory.addItem(itemStack);
			inventories.add(newInventory);
		}
		
		// Restore empty wand slot
		if (selectedItem != null) {
			hotbar.setItem(selectedItem, null);
		}
	}
	
	protected Inventory getDisplayInventory() {
		if (displayInventory == null) {
			displayInventory = InventoryUtils.createInventory(null, INVENTORY_SIZE, "Wand");
		}
		
		return displayInventory;
	}
	
	protected Inventory getInventoryByIndex(int inventoryIndex) {
		while (inventoryIndex >= inventories.size()) {
			inventories.add(InventoryUtils.createInventory(null, INVENTORY_SIZE, "Wand"));
		}
		return inventories.get(inventoryIndex);
	}
	
	protected Inventory getInventory(Integer slot) {
		Inventory inventory = hotbar;
		if (slot >= HOTBAR_SIZE) {
			int inventoryIndex = (slot - HOTBAR_SIZE) / INVENTORY_SIZE;
			inventory = getInventoryByIndex(inventoryIndex);
		}
		
		return inventory;
	}
	
	protected int getInventorySlot(Integer slot) {
		if (slot < HOTBAR_SIZE) {
			return slot;
		}
		
		return ((slot - HOTBAR_SIZE) % INVENTORY_SIZE);
	}
	
	protected void addToInventory(ItemStack itemStack, Integer slot) {
		if (slot == null) {
			addToInventory(itemStack);
			return;
		}
		
		Inventory inventory = getInventory(slot);
		slot = getInventorySlot(slot);
		
		ItemStack existing = inventory.getItem(slot);
		inventory.setItem(slot, itemStack);
		
		if (existing != null && existing.getType() != Material.AIR) {
			addToInventory(existing);
		}
	}
	
	protected void parseInventoryStrings(String spellString, String materialString) {
		hotbar.clear();
		inventories.clear();
		String[] spellNames = StringUtils.split(spellString, "|");
		for (String spellName : spellNames) {
			String[] pieces = spellName.split("@");
			Integer slot = parseSlot(pieces);
			ItemStack itemStack = createSpellItem(pieces[0]);
			if (itemStack == null) {
				controller.getPlugin().getLogger().warning("Unable to create spell icon for key " + pieces[0]);
				continue;
			}
			addToInventory(itemStack, slot);
		}
		String[] materialNames = StringUtils.split(materialString, "|");
		for (String materialName : materialNames) {
			String[] pieces = materialName.split("@");
			Integer slot = parseSlot(pieces);
			ItemStack itemStack = createMaterialItem(pieces[0]);
			if (itemStack == null) {
				controller.getPlugin().getLogger().warning("Unable to create material icon for key " + pieces[0]);
				continue;
			}
			addToInventory(itemStack, slot);
		}
		hasInventory = spellNames.length + materialNames.length > 1;
	}
	
	@SuppressWarnings("deprecation")
	protected ItemStack createSpellItem(String spellName) {
		Spell spell = controller.getSpell(spellName);
		if (spell == null) return null;
		MaterialAndData icon = spell.getIcon();
		if (icon == null) {
			controller.getPlugin().getLogger().warning("Unable to create spell icon for " + spell.getName() + ", missing material");	
		}
		ItemStack itemStack = null;
		ItemStack originalItemStack = null;
		try {
			originalItemStack = new ItemStack(icon.getMaterial(), 1, (short)0, (byte)icon.getData());
			itemStack = InventoryUtils.getCopy(originalItemStack);
		} catch (Exception ex) {
			itemStack = null;
		}
		if (itemStack == null) {
			controller.getPlugin().getLogger().warning("Unable to create spell icon with material " + icon.getMaterial().name());	
			return originalItemStack;
		}
		updateSpellName(itemStack, spell, true);
		return itemStack;
	}
	
	private String getActiveWandName(String materialKey) {
		Spell spell = controller.getSpell(activeSpell);
		return getActiveWandName(spell, materialKey);
	}
	
	@SuppressWarnings("deprecation")
	protected ItemStack createMaterialItem(String materialKey) {
		MaterialBrushData brushData = MaterialBrush.parseMaterialKey(materialKey, false);
		if (brushData == null) return null;
		
		Material material = brushData.getMaterial();
		byte dataId = brushData.getData();
		ItemStack originalItemStack = new ItemStack(material, 1, (short)0, (byte)dataId);	
		ItemStack itemStack = InventoryUtils.getCopy(originalItemStack);
		if (itemStack == null) {
			controller.getPlugin().getLogger().warning("Unable to create material icon for " + material.name() + ": " + originalItemStack.getType());	
			return originalItemStack;
		}
		ItemMeta meta = itemStack.getItemMeta();
		List<String> lore = new ArrayList<String>();
		if (material != null) {
			lore.add(ChatColor.GRAY + Messages.get("wand.building_material_info").replace("$material", MaterialBrush.getMaterialName(materialKey)));
			if (material == MaterialBrush.EraseMaterial) {
				lore.add(Messages.get("wand.erase_material_description"));
			} else if (material == MaterialBrush.CopyMaterial) {
				lore.add(Messages.get("wand.copy_material_description"));
			} else if (material == MaterialBrush.CloneMaterial) {
				lore.add(Messages.get("wand.clone_material_description"));
			} else if (material == MaterialBrush.ReplicateMaterial) {
				lore.add(Messages.get("wand.replicate_material_description"));
			} else if (material == MaterialBrush.MapMaterial) {
				lore.add(Messages.get("wand.map_material_description"));
			} else if (material == MaterialBrush.SchematicMaterial) {
				lore.add(Messages.get("wand.schematic_material_description").replace("$schematic", brushData.getSchematicName()));
			} else {
				lore.add(ChatColor.LIGHT_PURPLE + Messages.get("wand.building_material_description"));
			}
		}
		meta.setLore(lore);
		itemStack.setItemMeta(meta);
		updateMaterialName(itemStack, materialKey, true);
		return itemStack;
	}

	protected void saveState(boolean force) {
		if (force) suspendSave = false;
		saveState();
	}

	protected void saveState() {
		if (suspendSave) return;
		
		Object wandNode = InventoryUtils.createNode(item, "wand");
		
		InventoryUtils.setMeta(wandNode, "id", id);
		String wandMaterials = getMaterialString();
		String wandSpells = getSpellString();
		InventoryUtils.setMeta(wandNode, "materials", wandMaterials);
		InventoryUtils.setMeta(wandNode, "spells", wandSpells);
		InventoryUtils.setMeta(wandNode, "active_spell", activeSpell);
		InventoryUtils.setMeta(wandNode, "active_material", activeMaterial);
		InventoryUtils.setMeta(wandNode, "name", wandName);
		InventoryUtils.setMeta(wandNode, "description", description);
		InventoryUtils.setMeta(wandNode, "owner", owner);

		if (icon != null) {
			String iconKey = MaterialBrush.getMaterialKey(icon);
			if (iconKey != null && iconKey.length() > 0) {
				InventoryUtils.setMeta(wandNode, "icon", iconKey);
			}
		}
		
		if (template != null && template.length() > 0) {
			InventoryUtils.setMeta(wandNode, "template", template);
		}
	
		InventoryUtils.setMeta(wandNode, "cost_reduction", floatFormat.format(costReduction));
		InventoryUtils.setMeta(wandNode, "cooldown_reduction", floatFormat.format(cooldownReduction));
		InventoryUtils.setMeta(wandNode, "power", floatFormat.format(power));
		InventoryUtils.setMeta(wandNode, "protection", floatFormat.format(damageReduction));
		InventoryUtils.setMeta(wandNode, "protection_physical", floatFormat.format(damageReductionPhysical));
		InventoryUtils.setMeta(wandNode, "protection_projectiles", floatFormat.format(damageReductionProjectiles));
		InventoryUtils.setMeta(wandNode, "protection_falling", floatFormat.format(damageReductionFalling));
		InventoryUtils.setMeta(wandNode, "protection_fire", floatFormat.format(damageReductionFire));
		InventoryUtils.setMeta(wandNode, "protection_explosions", floatFormat.format(damageReductionExplosions));
		InventoryUtils.setMeta(wandNode, "haste", floatFormat.format(speedIncrease));
		InventoryUtils.setMeta(wandNode, "xp", Integer.toString(xp));
		InventoryUtils.setMeta(wandNode, "xp_regeneration", Integer.toString(xpRegeneration));
		InventoryUtils.setMeta(wandNode, "xp_max", Integer.toString(xpMax));
		InventoryUtils.setMeta(wandNode, "health_regeneration", Integer.toString(healthRegeneration));
		InventoryUtils.setMeta(wandNode, "hunger_regeneration", Integer.toString(hungerRegeneration));
		InventoryUtils.setMeta(wandNode, "uses", Integer.toString(uses));
		InventoryUtils.setMeta(wandNode, "has_inventory", Integer.toString((hasInventory ? 1 : 0)));
		InventoryUtils.setMeta(wandNode, "modifiable", Integer.toString((modifiable ? 1 : 0)));
		InventoryUtils.setMeta(wandNode, "effect_color", Integer.toString(effectColor, 16));
		InventoryUtils.setMeta(wandNode, "effect_bubbles", Integer.toString(effectBubbles ?  1 : 0));
		InventoryUtils.setMeta(wandNode, "effect_particle_data", Float.toString(effectParticleData));
		InventoryUtils.setMeta(wandNode, "effect_particle_count", Integer.toString(effectParticleCount));
		if (effectParticle != null) {
			InventoryUtils.setMeta(wandNode, "effect_particle", effectParticle.name());
		}
		if (mode != null) {
			InventoryUtils.setMeta(wandNode, "mode", mode.name());
		}
	}
	
	protected void loadState() {
		Object wandNode = InventoryUtils.getNode(item, "wand");
		if (wandNode == null) {
			controller.getPlugin().getLogger().warning("Found a wand with missing NBT data. This may be an old wand, or something may have wiped its data");
            return;
		}
		
		// Don't generate a UUID unless we need to, not sure how expensive that is.
		id = InventoryUtils.getMeta(wandNode, "id");
		id = id == null || id.length() == 0 ? UUID.randomUUID().toString() : id;
		wandName = InventoryUtils.getMeta(wandNode, "name", wandName);
		description = InventoryUtils.getMeta(wandNode, "description", description);
		owner = InventoryUtils.getMeta(wandNode, "owner", owner);
		template = InventoryUtils.getMeta(wandNode, "template", template);

		activeSpell = InventoryUtils.getMeta(wandNode, "active_spell", activeSpell);
		activeMaterial = InventoryUtils.getMeta(wandNode, "active_material", activeMaterial);
		
		String wandMaterials = InventoryUtils.getMeta(wandNode, "materials", "");
		String wandSpells = InventoryUtils.getMeta(wandNode, "spells", "");
		parseInventoryStrings(wandSpells, wandMaterials);
		
		costReduction = Float.parseFloat(InventoryUtils.getMeta(wandNode, "cost_reduction", floatFormat.format(costReduction)));
		cooldownReduction = Float.parseFloat(InventoryUtils.getMeta(wandNode, "cooldown_reduction", floatFormat.format(cooldownReduction)));
		power = Float.parseFloat(InventoryUtils.getMeta(wandNode, "power", floatFormat.format(power)));
		damageReduction = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection", floatFormat.format(damageReduction)));
		damageReductionPhysical = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection_physical", floatFormat.format(damageReductionPhysical)));
		damageReductionProjectiles = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection_projectiles", floatFormat.format(damageReductionProjectiles)));
		damageReductionFalling = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection_falling", floatFormat.format(damageReductionFalling)));
		damageReductionFire = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection_fire", floatFormat.format(damageReductionFire)));
		damageReductionExplosions = Float.parseFloat(InventoryUtils.getMeta(wandNode, "protection_explosions", floatFormat.format(damageReductionExplosions)));
		speedIncrease = Float.parseFloat(InventoryUtils.getMeta(wandNode, "haste", floatFormat.format(speedIncrease)));
		xp = Integer.parseInt(InventoryUtils.getMeta(wandNode, "xp", Integer.toString(xp)));
		xpRegeneration = Integer.parseInt(InventoryUtils.getMeta(wandNode, "xp_regeneration", Integer.toString(xpRegeneration)));
		xpMax = Integer.parseInt(InventoryUtils.getMeta(wandNode, "xp_max", Integer.toString(xpMax)));
		healthRegeneration = Integer.parseInt(InventoryUtils.getMeta(wandNode, "health_regeneration", Integer.toString(healthRegeneration)));
		hungerRegeneration = Integer.parseInt(InventoryUtils.getMeta(wandNode, "hunger_regeneration", Integer.toString(hungerRegeneration)));
		uses = Integer.parseInt(InventoryUtils.getMeta(wandNode, "uses", Integer.toString(uses)));
		hasInventory = Integer.parseInt(InventoryUtils.getMeta(wandNode, "has_inventory", (hasInventory ? "1" : "0"))) != 0;
		modifiable = Integer.parseInt(InventoryUtils.getMeta(wandNode, "modifiable", (modifiable ? "1" : "0"))) != 0;
		effectColor = Integer.parseInt(InventoryUtils.getMeta(wandNode, "effect_color", Integer.toString(effectColor, 16)), 16);
		effectBubbles = Integer.parseInt(InventoryUtils.getMeta(wandNode, "effect_bubbles", (effectBubbles ? "1" : "0"))) != 0;
		effectParticleData = Float.parseFloat(InventoryUtils.getMeta(wandNode, "effect_particle_data", floatFormat.format(effectParticleData)));
		effectParticleCount = Integer.parseInt(InventoryUtils.getMeta(wandNode, "effect_particle_count", Integer.toString(effectParticleCount)));
		parseParticleEffect(InventoryUtils.getMeta(wandNode, "effect_particle", effectParticle == null ? "" : effectParticle.name()));
		mode = parseWandMode(InventoryUtils.getMeta(wandNode, "mode", ""), mode);
		String iconKey = InventoryUtils.getMeta(wandNode, "icon", "");
		if (iconKey.length() > 0) {
			icon = MaterialBrush.parseMaterialKey(iconKey);
		} else {
			icon = null;
		}
	}

	protected void parseParticleEffect(String effectParticleName) {
		if (effectParticleName.length() > 0) {
			String testName = effectParticleName.toUpperCase().replace("_", "");
			try {
				for (ParticleType testType : ParticleType.values()) {
					String testTypeName = testType.name().replace("_", "");
					if (testTypeName.equals(testName)) {
						effectParticle = testType;
						break;
					}
				}
			} catch (Exception ex) {
				effectParticle = null;
			}
		} else {
			effectParticle = null;
		}
	}

	public void describe(CommandSender sender) {
		Object wandNode = InventoryUtils.getNode(item, "wand");
		if (wandNode == null) {
			sender.sendMessage("Found a wand with missing NBT data. This may be an old wand, or something may have wiped its data");
            return;
		}
		ChatColor wandColor = modifiable ? ChatColor.AQUA : ChatColor.RED;
		sender.sendMessage(wandColor + wandName);
		if (description.length() > 0) {
			sender.sendMessage(ChatColor.ITALIC + "" + ChatColor.GREEN + description);
		} else {
			sender.sendMessage(ChatColor.ITALIC + "" + ChatColor.GREEN + "(No Description)");
		}
		if (owner.length() > 0) {
			sender.sendMessage(ChatColor.ITALIC + "" + ChatColor.WHITE + owner);
		} else {
			sender.sendMessage(ChatColor.ITALIC + "" + ChatColor.WHITE + "(No Owner)");
		}
		
		for (String key : PROPERTY_KEYS) {
			String value = InventoryUtils.getMeta(wandNode, key);
			if (value != null && value.length() > 0) {
				sender.sendMessage(key + ": " + value);
			}
		}
	}

	public boolean removeMaterial(String materialKey) {
		if (!modifiable || materialKey == null) return false;
		
		if (isInventoryOpen()) {
			saveInventory();
		}
		if (materialKey.equals(activeMaterial)) {
			activeMaterial = null;
		}
		List<Inventory> allInventories = getAllInventories();
		boolean found = false;
		for (Inventory inventory : allInventories) {
			ItemStack[] items = inventory.getContents();
			for (int index = 0; index < items.length; index++) {
				ItemStack itemStack = items[index];
				if (itemStack != null && isBrush(itemStack)) {
					String itemKey = getMaterialKey(itemStack);
					if (itemKey.equals(materialKey)) {
						found = true;
						inventory.setItem(index, null);
					} else if (activeMaterial == null) {
						activeMaterial = materialKey;
					}
					if (found && activeMaterial != null) {
						break;
					}
				}
			}
		}
		updateActiveMaterial();
		updateInventory();
		updateName();
		updateLore();
		saveState();
		if (isInventoryOpen()) {
			updateInventory();
		}
		return found;
	}
	
	public boolean hasMaterial(String materialKey) {
		return getMaterialKeys().contains(materialKey);
	}
	
	public boolean hasSpell(String spellName) {
		return getSpells().contains(spellName);
	}
	
	public boolean addMaterial(String materialKey, boolean makeActive, boolean force) {
		if (!modifiable && !force) return false;
		
		boolean addedNew = !hasMaterial(materialKey);
		if (addedNew) {
			addToInventory(createMaterialItem(materialKey));
		}
		if (activeMaterial == null || activeMaterial.length() == 0 || makeActive) {
			setActiveMaterial(materialKey);
		} else {
			updateInventory();
		}
		updateLore();
		saveState();
		hasInventory = getSpells().size() + getMaterialKeys().size() > 1;
		
		return addedNew;
	}
	
	public boolean addMaterial(Material material, byte data, boolean makeActive, boolean force) {
		if (!modifiable && !force) return false;
		
		if (isInventoryOpen()) {
			saveInventory();
		}
		String materialKey = MaterialBrush.getMaterialKey(material, data, false);
		return addMaterial(materialKey, makeActive, force);
	}
	
	public boolean removeSpell(String spellName) {
		if (!modifiable) return false;
		
		if (isInventoryOpen()) {
			saveInventory();
		}
		if (spellName.equals(activeSpell)) {
			activeSpell = null;
		}
		
		List<Inventory> allInventories = getAllInventories();
		boolean found = false;
		for (Inventory inventory : allInventories) {
			ItemStack[] items = inventory.getContents();
			for (int index = 0; index < items.length; index++) {
				ItemStack itemStack = items[index];
				if (itemStack != null && itemStack.getType() != Material.AIR && !isWand(itemStack) && isSpell(itemStack)) {
					if (getSpell(itemStack).equals(spellName)) {
						found = true;
						inventory.setItem(index, null);
					} else if (activeSpell == null) {
						activeSpell = getSpell(itemStack);
					}
					if (found && activeSpell != null) {
						break;
					}
				}
			}
		}
		updateName();
		updateLore();
		saveState();
		updateInventory();
		
		return found;
	}
	
	public boolean addSpell(String spellName, boolean makeActive) {
		if (!modifiable) return false;
		
		if (isInventoryOpen()) {
			saveInventory();
		}
		boolean addedNew = !hasSpell(spellName);
		ItemStack spellItem = createSpellItem(spellName);
		if (spellItem == null) {
			controller.getPlugin().getLogger().info("Unknown spell: " + spellName);
			return false;
		}
		if (addedNew) {
			addToInventory(spellItem);
		}
		if (activeSpell == null || activeSpell.length() == 0 || makeActive) {
			setActiveSpell(spellName);
		} else {
			updateInventory();
		}
		hasInventory = getSpells().size() + getMaterialKeys().size() > 1;
		updateLore();
		saveState();
		
		return addedNew;
	}
	
	public boolean addSpell(String spellName) {
		return addSpell(spellName, false);
	}
	
	private String getSpellDisplayName(Spell spell, String materialKey) {
		String name = "";
		if (spell != null) {
			if (materialKey != null && (spell instanceof BrushSpell) && !((BrushSpell)spell).hasBrushOverride()) {
				String materialName = MaterialBrush.getMaterialName(materialKey);
				if (materialName == null) {
					materialName = "none";
				}
				name = ChatColor.GOLD + spell.getName() + ChatColor.GRAY + " " + materialName + ChatColor.WHITE;
			} else {
				name = ChatColor.GOLD + spell.getName() + ChatColor.WHITE;
			}
		}
		
		return name;
	}

	private String getActiveWandName(Spell spell, String materialKey) {

		// Build wand name
		ChatColor wandColor = modifiable ? ChatColor.AQUA : ChatColor.RED;
		String name = wandColor + wandName;
		
		// Add active spell to description
		if (spell != null) {
			name = getSpellDisplayName(spell, materialKey) + " (" + name + ChatColor.WHITE + ")";
		}
		int remaining = getRemainingUses();
		if (remaining > 0) {
			name = name + " : " + ChatColor.RED + Messages.get("wand.uses_remaining_brief").replace("$count", ((Integer)remaining).toString());
		}
		return name;
	}
	
	private String getActiveWandName(Spell spell) {
		return getActiveWandName(spell, activeMaterial);
	}
	
	private String getActiveWandName() {
		Spell spell = null;
		if (hasInventory) {
			spell = controller.getSpell(activeSpell);
		}
		return getActiveWandName(spell);
	}
	
	public void updateName(boolean isActive) {
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(isActive ? getActiveWandName() : wandName);
		item.setItemMeta(meta);
		
		// Reset Enchantment glow
		InventoryUtils.addGlow(item);

		// The all-important last step of restoring the meta state, something
		// the Anvil will blow away.
		saveState();
	}
	
	private void updateName() {
		updateName(true);
	}
	
	private String getLevelString(String prefix, float amount) {
		String suffix = "";

		if (amount > 1) {
			suffix = Messages.get("wand.enchantment_level_max");
		} else if (amount > 0.8) {
			suffix = Messages.get("wand.enchantment_level_5");
		} else if (amount > 0.6) {
			suffix = Messages.get("wand.enchantment_level_4");
		} else if (amount > 0.4) {
			suffix = Messages.get("wand.enchantment_level_3");
		} else if (amount > 0.2) {
			suffix = Messages.get("wand.enchantment_level_2");
		} else {
			 suffix = Messages.get("wand.enchantment_level_1");
		}
		return prefix + " " + suffix;
	}
	
	protected static String convertToHTML(String line) {
		int tagCount = 1;
		line = "<span style=\"color:white\">" + line;
		for (ChatColor c : ChatColor.values()) {
			tagCount += StringUtils.countMatches(line, c.toString());
			String replaceStyle = "";
			if (c == ChatColor.ITALIC) {
				replaceStyle = "font-style: italic";
			} else if (c == ChatColor.BOLD) {
				replaceStyle = "font-weight: bold";
			} else if (c == ChatColor.UNDERLINE) {
				replaceStyle = "text-decoration: underline";
			} else {
				String color = c.name().toLowerCase().replace("_", "");
				if (c == ChatColor.LIGHT_PURPLE) {
					color = "mediumpurple";
				}
				replaceStyle = "color:" + color;
			}
			line = line.replace(c.toString(), "<span style=\"" + replaceStyle + "\">");
		}
		for (int i = 0; i < tagCount; i++) {
			line += "</span>";
		}
		
		return line;
	}

	public String getHTMLDescription() {
		Collection<String> rawLore = getLore();
		Collection<String> lore = new ArrayList<String>();
		lore.add("<h2>" + convertToHTML(getActiveWandName()) + "</h2>");
 		for (String line : rawLore) {
			lore.add(convertToHTML(line));
		}
		
		return "<div style=\"background-color: black; margin: 8px; padding: 8px\">" + StringUtils.join(lore, "<br/>") + "</div>";
	}

	private List<String> getLore() {
		return getLore(getSpells().size(), getMaterialKeys().size());
	}
	
	private List<String> getLore(int spellCount, int materialCount) {
		List<String> lore = new ArrayList<String>();
		
		Spell spell = controller.getSpell(activeSpell);
		if (spell != null && spellCount == 1 && materialCount <= 1) {
			addSpellLore(spell, lore);
		} else {
			if (description.length() > 0) {
				lore.add(ChatColor.ITALIC + "" + ChatColor.GREEN + description);
			}
			if (owner.length() > 0) {
				String ownerDescription = Messages.get("wand.owner_description", "$name").replace("$name", owner);
				lore.add(ChatColor.ITALIC + "" + ChatColor.DARK_GREEN + ownerDescription);
			}
			
			lore.add(Messages.get("wand.spell_count").replace("$count", ((Integer)spellCount).toString()));
			if (materialCount > 0) {
				lore.add(Messages.get("wand.material_count").replace("$count", ((Integer)materialCount).toString()));
			}
		}
		int remaining = getRemainingUses();
		if (remaining > 0) {
			lore.add(ChatColor.RED + Messages.get("wand.uses_remaining").replace("$count", ((Integer)remaining).toString()));
		}
		if (usesMana()) {
			lore.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.ITALIC + Messages.get("wand.mana_amount").replace("$amount", ((Integer)xpMax).toString()));
			lore.add(ChatColor.RESET + "" + ChatColor.LIGHT_PURPLE + getLevelString(Messages.get("wand.mana_regeneration"), (float)xpRegeneration / (float)WandLevel.maxXpRegeneration));
		}
		if (costReduction > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.cost_reduction"), costReduction));
		if (cooldownReduction > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.cooldown_reduction"), cooldownReduction));
		if (power > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.power"), power));
		if (speedIncrease > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.haste"), speedIncrease));
		if (damageReduction > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection"), damageReduction));
		if (damageReduction < 1) {
			if (damageReductionPhysical > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection_physical"), damageReductionPhysical));
			if (damageReductionProjectiles > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection_projectile"), damageReductionProjectiles));
			if (damageReductionFalling > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection_fall"), damageReductionFalling));
			if (damageReductionFire > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection_fire"), damageReductionFire));
			if (damageReductionExplosions > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.protection_blast"), damageReductionExplosions));
		}
		if (healthRegeneration > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.health_regeneration"), healthRegeneration / WandLevel.maxRegeneration));
		if (hungerRegeneration > 0) lore.add(ChatColor.AQUA + getLevelString(Messages.get("wand.hunger_regeneration"), hungerRegeneration / WandLevel.maxRegeneration));
		return lore;
	}
	
	private void updateLore() {
		ItemMeta meta = item.getItemMeta();
		List<String> lore = getLore();
		meta.setLore(lore);
		
		item.setItemMeta(meta);
		InventoryUtils.addGlow(item);

		// Reset spell list and wand config
		saveState();
	}
	
	public int getRemainingUses() {
		return uses;
	}
	
	public void makeEnchantable(boolean enchantable) {
		// TODO: Support non-default items
		if (item.getType() == EnchantableWandMaterial || item.getType() == DefaultWandMaterial) {
			item.setType(enchantable ? EnchantableWandMaterial : DefaultWandMaterial);
		}
		updateName();
	}
	
	public static boolean hasActiveWand(Player player) {
		if (player == null) return false;
		ItemStack activeItem =  player.getInventory().getItemInHand();
		return isWand(activeItem);
	}
	
	public static Wand getActiveWand(MagicController spells, Player player) {
		ItemStack activeItem =  player.getInventory().getItemInHand();
		if (isWand(activeItem)) {
			return new Wand(spells, activeItem);
		}
		
		return null;
	}

	public static boolean isWand(ItemStack item) {
		return item != null && InventoryUtils.hasMeta(item, "wand");
	}

	public static boolean isSpell(ItemStack item) {
		return item != null && InventoryUtils.hasMeta(item, "spell");
	}

	public static boolean isBrush(ItemStack item) {
		return item != null && InventoryUtils.hasMeta(item, "brush");
	}
	
	public static String getSpell(ItemStack item) {
		if (!isSpell(item)) return null;
		
		Object spellNode = InventoryUtils.getNode(item, "spell");
		return InventoryUtils.getMeta(spellNode, "key");
	}

	public static String getMaterialKey(ItemStack item) {
		if (!isBrush(item)) return null;
		
		Object brushNode = InventoryUtils.getNode(item, "brush");
		return InventoryUtils.getMeta(brushNode, "key");
	}
	
	protected static String getMaterialKey(ItemStack itemStack, Integer index) {
		String materialKey = getMaterialKey(itemStack);
		if (materialKey == null) return null;
		
		if (index != null) {
			materialKey += "@" + index;
		}
		
		return materialKey;
	}

	protected void updateInventoryName(ItemStack item, boolean activeName) {
		if (isSpell(item)) {
			Spell spell = mage.getSpell(getSpell(item));
			if (spell != null) {
				updateSpellName(item, spell, activeName);
			}
		} else if (isBrush(item)) {
			updateMaterialName(item, getMaterialKey(item), activeName);
		}
	}
	
	protected void updateSpellName(ItemStack itemStack, Spell spell, boolean activeName) {
		ItemMeta meta = itemStack.getItemMeta();
		String displayName = null;
		if (activeName) {
			displayName = getActiveWandName(spell);
		} else {
			displayName = getSpellDisplayName(spell, activeMaterial);
		}
		meta.setDisplayName(displayName);
		List<String> lore = new ArrayList<String>();
		addSpellLore(spell, lore);
		meta.setLore(lore);
		itemStack.setItemMeta(meta);
		InventoryUtils.addGlow(itemStack);
		Object spellNode = InventoryUtils.createNode(itemStack, "spell");
		InventoryUtils.setMeta(spellNode, "key", spell.getKey());
	}
	
	protected void updateMaterialName(ItemStack itemStack, String materialKey, boolean activeName) {
		ItemMeta meta = itemStack.getItemMeta();
		String displayName = null;
		if (activeName) {
			displayName = getActiveWandName(materialKey);
		} else {
			displayName = MaterialBrush.getMaterialName(materialKey);
		}
		meta.setDisplayName(displayName);
		itemStack.setItemMeta(meta);
		Object brushNode = InventoryUtils.createNode(itemStack, "brush");
		InventoryUtils.setMeta(brushNode, "key", materialKey);
	}

	@SuppressWarnings("deprecation")
	private void updateInventory() {
		if (mage == null) return;
		if (!isInventoryOpen()) return;
		Player player = mage.getPlayer();
		if (player == null) return;
		
		WandMode wandMode = getMode();
		if (wandMode == WandMode.INVENTORY) {
			if (!mage.hasStoredInventory()) return;
			PlayerInventory inventory = player.getInventory();
			inventory.clear();
			updateHotbar(inventory);
			updateInventory(inventory, HOTBAR_SIZE);
			updateName();
			player.updateInventory();
		} else if (wandMode == WandMode.CHEST) {
			Inventory inventory = getDisplayInventory();
			inventory.clear();
			updateInventory(inventory, 0);
			player.updateInventory();
		}
	}
	
	private void updateHotbar(PlayerInventory playerInventory) {
		// Check for an item already in the player's held slot, which
		// we are about to replace with the wand.
		int currentSlot = playerInventory.getHeldItemSlot();
		ItemStack existingHotbar = hotbar.getItem(currentSlot);
		if (existingHotbar != null && existingHotbar.getType() != Material.AIR && !isWand(existingHotbar)) {
			// Toss the item back into the wand inventory, it'll find a home somewhere.
			addToInventory(existingHotbar);
			hotbar.setItem(currentSlot, null);
		}
		// Put the wand in the player's active slot.
		playerInventory.setItem(currentSlot, item);
		
		// Set hotbar items from remaining list
		for (int hotbarSlot = 0; hotbarSlot < HOTBAR_SIZE; hotbarSlot++) {
			if (hotbarSlot != currentSlot) {
				ItemStack hotbarItem = hotbar.getItem(hotbarSlot);
				updateInventoryName(hotbarItem, true);
				playerInventory.setItem(hotbarSlot, hotbarItem);
			}
		}
	}
	
	private void updateInventory(Inventory targetInventory, int startOffset) {
		// Set inventory from current page
		if (openInventoryPage < inventories.size()) {
			Inventory inventory = inventories.get(openInventoryPage);
			ItemStack[] contents = inventory.getContents();
			for (int i = 0; i < contents.length; i++) {
				ItemStack inventoryItem = contents[i];
				updateInventoryName(inventoryItem, false);
				targetInventory.setItem(i + startOffset, inventoryItem);
			}	
		}
	}
	
	protected void addSpellLore(Spell spell, List<String> lore) {
		String description = spell.getDescription();
		String usage = spell.getUsage();
		if (description != null && description.length() > 0) {
			lore.add(description);
		}
		if (usage != null && usage.length() > 0) {
			lore.add(usage);
		}
		List<CastingCost> costs = spell.getCosts();
		if (costs != null) {
			for (CastingCost cost : costs) {
				if (cost.hasCosts(this)) {
					lore.add(ChatColor.YELLOW + Messages.get("wand.costs_description").replace("$description", cost.getFullDescription(this)));
				}
			}
		}
		List<CastingCost> activeCosts = spell.getActiveCosts();
		if (activeCosts != null) {
			for (CastingCost cost : activeCosts) {
				if (cost.hasCosts(this)) {
					lore.add(ChatColor.YELLOW + Messages.get("wand.active_costs_description").replace("$description", cost.getFullDescription(this)));
				}
			}
		}
	}
	
	protected Inventory getOpenInventory() {
		while (openInventoryPage >= inventories.size()) {
			inventories.add(InventoryUtils.createInventory(null, INVENTORY_SIZE, "Wand"));
		}
		return inventories.get(openInventoryPage);
	}
	
	public void saveInventory() {
		if (mage == null) return;
		if (!isInventoryOpen()) return;
		if (mage.getPlayer() == null) return;
		if (getMode() != WandMode.INVENTORY) return;
		if (!mage.hasStoredInventory()) return;
		
		// Fill in the hotbar
		Player player = mage.getPlayer();
		PlayerInventory playerInventory = player.getInventory();
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			ItemStack playerItem = playerInventory.getItem(i);
			if (isWand(playerItem)) {
				playerItem = null;
			}
			hotbar.setItem(i, playerItem);
		}
		
		// Fill in the active inventory page
		Inventory openInventory = getOpenInventory();
		for (int i = 0; i < openInventory.getSize(); i++) {
			openInventory.setItem(i, playerInventory.getItem(i + HOTBAR_SIZE));
		}
		saveState();
	}

	public static boolean isActive(Player player) {
		ItemStack activeItem = player.getInventory().getItemInHand();
		return isWand(activeItem);
	}
	
	protected void randomize(int totalLevels, boolean additive) {
		if (!wandTemplates.containsKey("random")) return;	
		if (!additive) {
			wandName = Messages.get("wands.random.name", wandName);
		}
		
		int maxLevel = WandLevel.getMaxLevel();
		int addLevels = Math.min(totalLevels, maxLevel);
		while (addLevels > 0) {
			WandLevel.randomizeWand(this, additive, addLevels);
			totalLevels -= maxLevel;
			addLevels = Math.min(totalLevels, maxLevel);
			additive = true;
		}
	}
	
	public static Wand createWand(MagicController controller, String templateName) {
		return createWand(controller, templateName, null);
	}
	
	public static Wand createWand(MagicController controller, String templateName, Mage owner) {
		Wand wand = new Wand(controller);
		wand.suspendSave = true;
		String wandName = Messages.get("wand.default_name");
		String wandDescription = "";

		// Check for default wand
		if ((templateName == null || templateName.length() == 0) && wandTemplates.containsKey("default"))
		{
			templateName = "default";
		}
		
		// See if there is a template with this key
		if (templateName != null && templateName.length() > 0) {
			if ((templateName.equals("random") || templateName.startsWith("random(")) && wandTemplates.containsKey("random")) {
				int level = 1;
				if (!templateName.equals("random")) {
					String randomLevel = templateName.substring(templateName.indexOf('(') + 1, templateName.length() - 1);
					level = Integer.parseInt(randomLevel);
				}
				ConfigurationNode randomTemplate = wandTemplates.get("random");
				wand.randomize(level, false);
				wand.modifiable = (boolean)randomTemplate.getBoolean("modifiable", true);
				wand.saveState(true);
				return wand;
			}
			
			if (!wandTemplates.containsKey(templateName)) {
				return null;
			}
			ConfigurationNode wandConfig = wandTemplates.get(templateName);
			wandName = wandConfig.getString("name", wandName);
			wandName = Messages.get("wands." + templateName + ".name", wandName);
			wandDescription = wandConfig.getString("description", wandDescription);
			wandDescription = Messages.get("wands." + templateName + ".description", wandDescription);
			List<Object> spellList = wandConfig.getList("spells");
			if (spellList != null) {
				for (Object spellName : spellList) {			
					wand.addSpell((String)spellName);
				}
			}
			List<Object> materialList = wandConfig.getList("materials");
			if (materialList != null) {
				for (Object materialKey : materialList) {
					if (!MaterialBrush.isValidMaterial((String)materialKey, false)) {
						controller.getPlugin().getLogger().info("Unknown material: " + materialKey);
					} else {
						wand.addMaterial((String)materialKey, false, true);
					}
				}
			}
			
			wand.configureProperties(wandConfig);
			wand.setTemplate(templateName);
			
			if (wandConfig.getBoolean("organize", false)) {
				wand.organizeInventory(owner);
			}
		}

		if (owner != null) {	
			wand.takeOwnership(owner.getPlayer());
		}
		wand.setDescription(wandDescription);
		wand.setName(wandName);
		wand.saveState(true);
		
		return wand;
	}
	
	public void add(Wand other) {
		if (!modifiable || !other.modifiable) return;
		
		costReduction = Math.max(costReduction, other.costReduction);
		power = Math.max(power, other.power);
		damageReduction = Math.max(damageReduction, other.damageReduction);
		damageReductionPhysical = Math.max(damageReductionPhysical, other.damageReductionPhysical);
		damageReductionProjectiles = Math.max(damageReductionProjectiles, other.damageReductionProjectiles);
		damageReductionFalling = Math.max(damageReductionFalling, other.damageReductionFalling);
		damageReductionFire = Math.max(damageReductionFire, other.damageReductionFire);
		damageReductionExplosions = Math.max(damageReductionExplosions, other.damageReductionExplosions);
		healthRegeneration = Math.max(healthRegeneration, other.healthRegeneration);
		hungerRegeneration = Math.max(hungerRegeneration, other.hungerRegeneration);
		speedIncrease = Math.max(speedIncrease, other.speedIncrease);
		
		// Mix colors
		Color color1 = Color.fromBGR(effectColor);
		Color color2 = Color.fromBGR(other.effectColor);
		Color newColor = color1.mixColors(color2);
		effectColor = newColor.asRGB();
		
		effectBubbles = effectBubbles || other.effectBubbles;
		if (effectParticle == null) {
			effectParticle = other.effectParticle;
			effectParticleData = other.effectParticleData;
			effectParticleCount = other.effectParticleCount;
		}
		
		if (other.template != null && other.template.length() > 0) {
			template = other.template;
		}
		
		// Don't need mana if cost-free
		if (costReduction >= 1) {
			xpRegeneration = 0;
			xpMax = 0;
			xp = 0;
		} else {
			xpRegeneration = Math.max(xpRegeneration, other.xpRegeneration);
			xpMax = Math.max(xpMax, other.xpMax);
			xp = Math.max(xp, other.xp);
		}
		
		// Eliminate limited-use wands
		if (uses == 0 || other.uses == 0) {
			uses = 0;
		} else {
			// Otherwise add them
			uses = uses + other.uses;
		}
		
		// Add spells
		Set<String> spells = other.getSpells();
		for (String spell : spells) {
			addSpell(spell, false);
		}

		// Add materials
		Set<String> materials = other.getMaterialKeys();
		for (String material : materials) {
			addMaterial(material, false, true);
		}

		saveState();
		updateName();
		updateLore();
	}
	
	public void configureProperties(ConfigurationNode wandConfig) {
		configureProperties(wandConfig, false);
	}
	
	public void configureProperties(ConfigurationNode wandConfig, boolean safe) {
		modifiable = (boolean)wandConfig.getBoolean("modifiable", modifiable);
		float _costReduction = (float)wandConfig.getDouble("cost_reduction", costReduction);
		costReduction = safe ? Math.max(_costReduction, costReduction) : _costReduction;
		float _cooldownReduction = (float)wandConfig.getDouble("cooldown_reduction", cooldownReduction);
		cooldownReduction = safe ? Math.max(_cooldownReduction, cooldownReduction) : _cooldownReduction;
		float _power = (float)wandConfig.getDouble("power", power);
		power = safe ? Math.max(_power, power) : _power;
		float _damageReduction = (float)wandConfig.getDouble("protection", damageReduction);
		damageReduction = safe ? Math.max(_damageReduction, damageReduction) : _damageReduction;
		float _damageReductionPhysical = (float)wandConfig.getDouble("protection_physical", damageReductionPhysical);
		damageReductionPhysical = safe ? Math.max(_damageReductionPhysical, damageReductionPhysical) : _damageReductionPhysical;
		float _damageReductionProjectiles = (float)wandConfig.getDouble("protection_projectiles", damageReductionProjectiles);
		damageReductionProjectiles = safe ? Math.max(_damageReductionProjectiles, damageReductionPhysical) : _damageReductionProjectiles;
		float _damageReductionFalling = (float)wandConfig.getDouble("protection_falling", damageReductionFalling);
		damageReductionFalling = safe ? Math.max(_damageReductionFalling, damageReductionFalling) : _damageReductionFalling;
		float _damageReductionFire = (float)wandConfig.getDouble("protection_fire", damageReductionFire);
		damageReductionFire = safe ? Math.max(_damageReductionFire, damageReductionFire) : _damageReductionFire;
		float _damageReductionExplosions = (float)wandConfig.getDouble("protection_explosions", damageReductionExplosions);
		damageReductionExplosions = safe ? Math.max(_damageReductionExplosions, damageReductionExplosions) : _damageReductionExplosions;
		int _xpRegeneration = wandConfig.getInt("xp_regeneration", xpRegeneration);
		xpRegeneration = safe ? Math.max(_xpRegeneration, xpRegeneration) : _xpRegeneration;
		int _xpMax = wandConfig.getInt("xp_max", xpMax);
		xpMax = safe ? Math.max(_xpMax, xpMax) : _xpMax;
		int _xp = wandConfig.getInt("xp", xp);
		xp = safe ? Math.max(_xp, xp) : _xp;
		int _healthRegeneration = wandConfig.getInt("health_regeneration", healthRegeneration);
		healthRegeneration = safe ? Math.max(_healthRegeneration, healthRegeneration) : _healthRegeneration;
		int _hungerRegeneration = wandConfig.getInt("hunger_regeneration", hungerRegeneration);
		hungerRegeneration = safe ? Math.max(_hungerRegeneration, hungerRegeneration) : _hungerRegeneration;
		int _uses = wandConfig.getInt("uses", uses);
		uses = safe ? Math.max(_uses, uses) : _uses;
		float _speedIncrease = (float)wandConfig.getDouble("haste", speedIncrease);
		speedIncrease = safe ? Math.max(_speedIncrease, speedIncrease) : _speedIncrease;
		
		if (wandConfig.containsKey("effect_color") && !safe) {
			try {
				effectColor = Integer.parseInt(wandConfig.getString("effect_color", "0"), 16);
			} catch (Exception ex) {
				
			}
		}
		if (wandConfig.containsKey("effect_bubbles")) {
			boolean _effectBubbles = (boolean)wandConfig.getBoolean("effect_bubbles", effectBubbles);
			effectBubbles = safe ? _effectBubbles || effectBubbles : _effectBubbles;
		}
		if (wandConfig.containsKey("effect_particle") && !safe) {
			parseParticleEffect(wandConfig.getString("effect_particle"));
		}
		if (wandConfig.containsKey("effect_particle_data") && !safe) {
			effectParticleData = Float.parseFloat(wandConfig.getString("effect_particle_data"));
		}
		if (wandConfig.containsKey("effect_particle_count") && !safe) {
			effectParticleCount = Integer.parseInt(wandConfig.getString("effect_particle_count"));
		}
		mode = parseWandMode(wandConfig.getString("mode"), mode);
		
		owner = wandConfig.getString("owner", owner);
		description = wandConfig.getString("description", description);
		
		if (wandConfig.containsKey("icon")) {
			setIcon(wandConfig.getMaterialAndData("icon"));
		}
		
		template = wandConfig.getString(template, template);
		
		saveState();
		updateName();
		updateLore();
	}
	
	public static void loadTemplates(ConfigurationNode properties) {
		wandTemplates.clear();
		
		List<String> wandKeys = properties.getKeys();
		for (String key : wandKeys)
		{
			ConfigurationNode wandNode = properties.getNode(key);
			wandNode.setProperty("key", key);
			ConfigurationNode existing = wandTemplates.get(key);
			if (existing != null) {
				List<String> overrideKeys = existing.getKeys();
				for (String propertyKey : overrideKeys) {
					existing.setProperty(propertyKey, existing.getProperty(key));
				}
			} else {
				wandTemplates.put(key,  wandNode);
			}
			if (!wandNode.getBoolean("enabled", true)) {
				wandTemplates.remove(key);
			}
			if (key.equals("random")) {
				WandLevel.mapLevels(wandNode);
			}
		}
	}
	
	public static Collection<String> getWandKeys() {
		return wandTemplates.keySet();
	}
	
	public static Collection<ConfigurationNode> getWandTemplates() {
		return wandTemplates.values();
	}
	
	public static WandMode parseWandMode(String modeString, WandMode defaultValue) {
		for (WandMode testMode : WandMode.values()) {
			if (testMode.name().equalsIgnoreCase(modeString)) {
				return testMode;
			}
		}
		
		return defaultValue;
	}
	
	private void updateActiveMaterial() {
		if (mage == null) return;
		
		if (activeMaterial == null) {
			mage.clearBuildingMaterial();
		} else {
			MaterialBrush brush = mage.getBrush();
			brush.update(activeMaterial);
		}
	}
	
	public void toggleInventory() {
		if (!hasInventory) {
			return;
		}
		if (!isInventoryOpen()) {
			openInventory();
		} else {
			closeInventory();
		}
	}
	
	@SuppressWarnings("deprecation")
	public void cycleInventory() {
		if (!hasInventory) {
			return;
		}
		if (isInventoryOpen()) {
			saveInventory();
			openInventoryPage = (openInventoryPage + 1) % inventories.size();
			updateInventory();
			if (mage != null && inventories.size() > 1) {
				mage.playSound(Sound.CHEST_OPEN, 0.3f, 1.5f);
				mage.getPlayer().updateInventory();
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	private void openInventory() {
		if (mage == null) return;
		
		WandMode wandMode = getMode();
		if (wandMode == WandMode.CHEST) {
			// Hacky work-around for switching between modes. This mode has no hotbar!
			for (int i = 0; i < HOTBAR_SIZE; i++) {
				// Put hotbar items in main inventory since we don't show the hotbar in chest mode.
				ItemStack hotbarItem = hotbar.getItem(i);
				if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
					hotbar.setItem(i, null);
					addToInventory(hotbarItem);
				}
			}	
			
			inventoryIsOpen = true;
			mage.playSound(Sound.CHEST_OPEN, 0.4f, 0.2f);
			updateInventory();
			mage.getPlayer().openInventory(getDisplayInventory());
		} else if (wandMode == WandMode.INVENTORY) {
			if (mage.hasStoredInventory()) return;
			if (mage.storeInventory()) {
				inventoryIsOpen = true;
				mage.playSound(Sound.CHEST_OPEN, 0.4f, 0.2f);
				updateInventory();
				mage.getPlayer().updateInventory();
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	public void closeInventory() {
		if (!isInventoryOpen()) return;
		saveInventory();
		inventoryIsOpen = false;
		if (mage != null) {
			mage.playSound(Sound.CHEST_CLOSE, 0.4f, 0.2f);
			if (getMode() == WandMode.INVENTORY) {
				mage.restoreInventory();
				Player player = mage.getPlayer();
				player.setItemInHand(item);
				player.updateInventory();
			} else {
				mage.getPlayer().closeInventory();
			}
		}
		saveState();
	}
	
	protected void updateSpeed(Player player) {
		if (speedIncrease > 0) {
			try {
				float newWalkSpeed = defaultWalkSpeed + (speedIncrease * WandLevel.maxWalkSpeedIncrease);
				newWalkSpeed = Math.min(WandLevel.maxWalkSpeed, newWalkSpeed);
				if (newWalkSpeed != player.getWalkSpeed()) {
					player.setWalkSpeed(newWalkSpeed);
				}
				float newFlySpeed = defaultFlySpeed + (speedIncrease * WandLevel.maxFlySpeedIncrease);
				newFlySpeed = Math.min(WandLevel.maxFlySpeed, newFlySpeed);
				if (newFlySpeed != player.getFlySpeed()) {
					player.setFlySpeed(newFlySpeed);
				}
			} catch(Exception ex2) {
				try {
					player.setWalkSpeed(defaultWalkSpeed);
					player.setFlySpeed(defaultFlySpeed);
				}  catch(Exception ex) {
					
				}
			}
		}
	}
	
	public void fill(Player player) {
		List<Spell> allSpells = controller.getAllSpells();

		for (Spell spell : allSpells)
		{
			if (spell.hasSpellPermission(player) && spell.getIcon().getMaterial() != Material.AIR)
			{
				addSpell(spell.getKey());
			}
		}
	}
	
	public void activate(Mage mage) {
		Player player = mage.getPlayer();
		if (!Wand.hasActiveWand(player)) {
			controller.getLogger().warning("Wand activated without holding a wand!");
			try {
				throw new Exception("Wand activated without holding a wand!");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			return;
		}
		
		activate(mage, player.getItemInHand());
	}
		
	public void activate(Mage mage, ItemStack wandItem) {
		if (mage == null || wandItem == null) return;
		
		// Update held item, it may have been copied since this wand was created.
		this.item = wandItem;
		
		// Check for an empty wand and auto-fill
		if (controller.fillWands()) {
			if (getSpells().size() == 0) {
				fill(mage.getPlayer());
			}
		}
		
		this.mage = mage;
		Player player = mage.getPlayer();
		saveState();
		
		mage.setActiveWand(this);
		if (owner.length() == 0) {
			takeOwnership(player);
		}
		updateSpeed(player);
		if (usesMana()) {
			storedXpLevel = player.getLevel();
			storedXpProgress = player.getExp();
			storedXp = 0;
			updateMana();
		}
		updateActiveMaterial();
		updateName();
		updateLore();
		
		updateEffects();
	}
	
	protected void updateEffects() {
		if (mage == null) return;
		Player player = mage.getPlayer();
		if (player == null) return;
		
		// Update Bubble effects effects
		if (effectBubbles) {
			InventoryUtils.addPotionEffect(player, effectColor);
		}
		
		// TODO: More customization?
		if (effectParticle != null) {
			if ((particleCounter++ % particleFrequency) == 0) {
				if (effectPlayer == null) {
					effectPlayer = new EffectRing(controller.getPlugin());
					effectPlayer.setParticleCount(2);
					effectPlayer.setIterations(2);
					effectPlayer.setRadius(2);
					effectPlayer.setSize(5);
					effectPlayer.setMaterial(mage.getLocation().getBlock().getRelative(BlockFace.DOWN));
				}
				effectPlayer.setParticleType(effectParticle);
				effectPlayer.setParticleData(effectParticleData);
				effectPlayer.setParticleCount(effectParticleCount);
				effectPlayer.start(player.getEyeLocation(), null);
			}
		}
	}
	
	protected void updateMana() {
		if (mage != null && xpMax > 0 && xpRegeneration > 0) {
			Player player = mage.getPlayer();
			if (displayManaAsBar) {
				player.setLevel(0);
				player.setExp((float)xp / (float)xpMax);
			} else {
				player.setLevel(xp);
				player.setExp(0);
			}
		}
	}
	
	public boolean isInventoryOpen() {
		return mage != null && inventoryIsOpen;
	}
	
	public void deactivate() {
		if (mage == null) return;
		saveState();

		if (effectBubbles) {
			InventoryUtils.removePotionEffect(mage.getPlayer());
		}
		
		// This is a tying wands together with other spells, potentially
		// But with the way the mana system works, this seems like the safest route.
		mage.deactivateAllSpells();
		
		if (isInventoryOpen()) {
			closeInventory();
		}
		
		// Extra just-in-case
		mage.restoreInventory();
		
		if (usesMana()) {
			mage.getPlayer().setExp(storedXpProgress);
			mage.getPlayer().setLevel(storedXpLevel);
			mage.getPlayer().giveExp(storedXp);
			storedXp = 0;
			storedXpProgress = 0;
			storedXpLevel = 0;
		}
		if (speedIncrease > 0) {
			try {
				mage.getPlayer().setWalkSpeed(defaultWalkSpeed);
				mage.getPlayer().setFlySpeed(defaultFlySpeed);
			}  catch(Exception ex) {
				
			}
		}
		mage.setActiveWand(null);
		mage = null;
	}
	
	public Spell getActiveSpell() {
		if (mage == null) return null;
		return mage.getSpell(activeSpell);
	}
	
	public boolean cast() {
		Spell spell = getActiveSpell();
		if (spell != null) {
			if (spell.cast()) {
				use();
				return true;
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("deprecation")
	protected void use() {
		if (mage == null) return;
		if (uses > 0) {
			uses--;
			if (uses <= 0) {
				Player player = mage.getPlayer();
				mage.playSound(Sound.ITEM_BREAK, 1.0f, 0.8f);
				PlayerInventory playerInventory = player.getInventory();
				playerInventory.setItemInHand(new ItemStack(Material.AIR, 1));
				player.updateInventory();
				deactivate();
			} else {
				updateName();
				updateLore();
				saveState();
			}
		}
	}
	
	public void onPlayerExpChange(PlayerExpChangeEvent event) {
		if (mage == null) return;
		
		if (usesMana()) {
			storedXp += event.getAmount();
			event.setAmount(0);
		}
	}
	
	public void tick() {
		if (mage == null) return;
		
		Player player = mage.getPlayer();
		updateSpeed(player);
		if (usesMana()) {
			xp = Math.min(xpMax, xp + xpRegeneration);
			updateMana();
		}
		double maxHealth = player.getMaxHealth();
		if (healthRegeneration > 0 && player.getHealth() < maxHealth) {
			player.setHealth(Math.min(maxHealth, player.getHealth() + healthRegeneration));
		}
		double maxFoodLevel = 20;
		if (hungerRegeneration > 0 && player.getFoodLevel() < maxFoodLevel) {
			player.setExhaustion(0);
			player.setFoodLevel(Math.min(20, player.getFoodLevel() + hungerRegeneration));
		}
		if (damageReductionFire > 0 && player.getFireTicks() > 0) {
			player.setFireTicks(0);
		}
		
		updateEffects();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null) return false;
		if (!(other instanceof Wand)) return false;
		
		Wand otherWand =  ((Wand)other);
		if (this.id == null || otherWand.id == null) return false;
		
		return otherWand.id.equals(this.id);
	}
	
	public MagicController getMaster() {
		return controller;
	}
	
	public void cycleSpells(ItemStack newItem) {
		if (isWand(newItem)) item = newItem;
		
		Set<String> spellsSet = getSpells();
		ArrayList<String> spells = new ArrayList<String>(spellsSet);
		if (spells.size() == 0) return;
		if (activeSpell == null) {
			activeSpell = spells.get(0).split("@")[0];
			return;
		}
		
		int spellIndex = 0;
		for (int i = 0; i < spells.size(); i++) {
			if (spells.get(i).split("@")[0].equals(activeSpell)) {
				spellIndex = i;
				break;
			}
		}
		
		spellIndex = (spellIndex + 1) % spells.size();
		setActiveSpell(spells.get(spellIndex).split("@")[0]);
	}
	
	public void cycleMaterials(ItemStack newItem) {
		if (isWand(newItem)) item = newItem;
		
		Set<String> materialsSet = getMaterialKeys();
		ArrayList<String> materials = new ArrayList<String>(materialsSet);
		if (materials.size() == 0) return;
		if (activeMaterial == null) {
			activeMaterial = materials.get(0).split("@")[0];
			return;
		}
		
		int materialIndex = 0;
		for (int i = 0; i < materials.size(); i++) {
			if (materials.get(i).split("@")[0].equals(activeMaterial)) {
				materialIndex = i;
				break;
			}
		}
		
		materialIndex = (materialIndex + 1) % materials.size();
		activateBrush(materials.get(materialIndex).split("@")[0]);
	}
	
	public boolean hasExperience() {
		return xpRegeneration > 0;
	}
	
	public void organizeInventory(Mage mage) {
		WandOrganizer organizer = new WandOrganizer(this, mage);
		organizer.organize();
		openInventoryPage = 0;
		saveState();
	}
	
	public void organizeInventory() {
		WandOrganizer organizer = new WandOrganizer(this, null);
		organizer.organize();
		openInventoryPage = 0;
		saveState();
	}
	
	public Mage getActivePlayer() {
		return mage;
	}
	
	public String getId() {
		return this.id;
	}
	
	protected void clearInventories() {
		inventories.clear();
		hotbar.clear();
	}
	
	public int getEffectColor() {
		return effectColor;
	}
	
	public Inventory getHotbar() {
		return hotbar;
	}
	
	public WandMode getMode() {
		return mode != null ? mode : controller.getDefaultWandMode();
	}
}

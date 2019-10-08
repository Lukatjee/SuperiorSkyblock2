package com.bgsoftware.superiorskyblock.handlers;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.handlers.SchematicManager;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.hooks.FAWEHook;
import com.bgsoftware.superiorskyblock.schematics.WorldEditSchematic;
import com.bgsoftware.superiorskyblock.utils.FileUtils;
import com.bgsoftware.superiorskyblock.utils.tags.IntTag;
import com.bgsoftware.superiorskyblock.utils.tags.StringTag;
import com.bgsoftware.superiorskyblock.utils.threads.Executor;
import com.google.common.collect.Lists;

import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.schematics.SuperiorSchematic;
import com.bgsoftware.superiorskyblock.schematics.TagBuilder;
import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.utils.tags.ByteTag;
import com.bgsoftware.superiorskyblock.utils.tags.CompoundTag;
import com.bgsoftware.superiorskyblock.utils.tags.ListTag;
import com.bgsoftware.superiorskyblock.utils.tags.NBTInputStream;
import com.bgsoftware.superiorskyblock.utils.tags.NBTOutputStream;
import com.bgsoftware.superiorskyblock.utils.tags.Tag;
import com.bgsoftware.superiorskyblock.wrappers.SchematicPosition;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"WeakerAccess", "ResultOfMethodCallIgnored"})
public final class SchematicsHandler implements SchematicManager {

    private static String version = Bukkit.getBukkitVersion().split("-")[0];
    private SuperiorSkyblockPlugin plugin;

    private Map<String, Schematic> schematics = new HashMap<>();

    public SchematicsHandler(SuperiorSkyblockPlugin plugin){
        this.plugin = plugin;

        Executor.sync(() -> {
            File schematicsFolder = new File(plugin.getDataFolder(), "schematics");

            if(!schematicsFolder.exists()) {
                schematicsFolder.mkdirs();
                FileUtils.saveResource("schematics/normal.schematic");
                FileUtils.saveResource("schematics/mycel.schematic");
                FileUtils.saveResource("schematics/desert.schematic");
            }

            //noinspection ConstantConditions
            for(File schemFile : schematicsFolder.listFiles()){
                Schematic schematic = loadFromFile(schemFile);
                if(schematic != null) {
                    schematics.put(schemFile.getName().replace(".schematic", "").replace(".schem", ""), schematic);
                    SuperiorSkyblockPlugin.log("Successfully loaded schematic " + schemFile.getName() + " (" +
                            (schematic instanceof WorldEditSchematic ? "WorldEdit" : "SuperiorSkyblock") + ")");
                }
                else{
                    SuperiorSkyblockPlugin.log("Couldn't load schematic " + schemFile.getName() + ".");
                }
            }
        });
    }

    public Schematic getSchematic(String name) {
        return schematics.get(name);
    }

    public List<String> getSchematics(){
        return Lists.newArrayList(schematics.keySet());
    }

    public void saveSchematic(SuperiorPlayer superiorPlayer, String schematicName){
        Location pos1 = superiorPlayer.getSchematicPos1().parse(), pos2 = superiorPlayer.getSchematicPos2().parse();
        Location min = new Location(pos1.getWorld(),
                Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        Location offset = superiorPlayer.getLocation().clone().subtract(min.clone().add(0, 1, 0));
        saveSchematic(superiorPlayer.getSchematicPos1().parse(), superiorPlayer.getSchematicPos2().parse(),
                offset.getBlockX(), offset.getBlockY(), offset.getBlockZ(), schematicName, () ->
                Locale.SCHEMATIC_SAVED.send(superiorPlayer));
        superiorPlayer.setSchematicPos1(null);
        superiorPlayer.setSchematicPos2(null);
    }

    public void saveSchematic(Location pos1, Location pos2, int offsetX, int offsetY, int offsetZ, String schematicName){
        saveSchematic(pos1, pos2, offsetX, offsetY, offsetZ, schematicName, null);
    }

    public void saveSchematic(Location pos1, Location pos2, int offsetX, int offsetY, int offsetZ, String schematicName, Runnable runnable){
        if(Bukkit.isPrimaryThread() && !Bukkit.getBukkitVersion().contains("1.14")){
            Executor.async(() -> saveSchematic(pos1, pos2, offsetX, offsetY, offsetZ, schematicName, runnable));
            return;
        }

        World world = pos1.getWorld();
        Location min = new Location(world, Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(world, Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));

        int xSize = max.getBlockX() - min.getBlockX();
        int ySize = max.getBlockY() - min.getBlockY();
        int zSize = max.getBlockZ() - min.getBlockZ();

        List<Tag> blocks = new ArrayList<>(), entities = new ArrayList<>();

        for(int x = 0; x <= xSize; x++){
            for(int z = 0; z <= zSize; z++){
                for(int y = 0; y <= ySize; y++){
                    int _x = x + min.getBlockX(), _y = y + min.getBlockY(),  _z = z + min.getBlockZ();
                    Block block = world.getBlockAt(_x, _y, _z);

                    if(block.getType() != Material.AIR) {
                        TagBuilder tagBuilder = new TagBuilder().withBlockPosition(SchematicPosition.of(x, y, z)).withCombinedId(getCombinedId(block));

                        if(block.getState() instanceof Banner){
                            tagBuilder.applyBanner((Banner) block.getState());
                        }
                        else if(block.getState() instanceof InventoryHolder){
                            tagBuilder.applyContents(((InventoryHolder) block.getState()).getInventory().getContents());
                        }
                        else if(block.getType() == Material.FLOWER_POT){
                            tagBuilder.applyFlower(getFlower(block));
                        }
                        else if(block.getState() instanceof Skull){
                            tagBuilder.applySkull((Skull) block.getState());
                        }else if(block.getState() instanceof Sign){
                            tagBuilder.applySign((Sign) block.getState());
                        }

                        blocks.add(tagBuilder.build());
                    }
                }
            }
        }

        Location center = new Location(world, xSize / 2, ySize / 2, zSize / 2).add(min);
        for(LivingEntity livingEntity : getEntities(min, max)){
            entities.add(new TagBuilder().applyEntity(livingEntity, center).build());
        }

        Map<String, Tag> compoundValue = new HashMap<>();
        compoundValue.put("xSize", new ByteTag((byte) xSize));
        compoundValue.put("ySize", new ByteTag((byte) ySize));
        compoundValue.put("zSize", new ByteTag((byte) zSize));
        compoundValue.put("blocks", new ListTag(CompoundTag.class, blocks));
        compoundValue.put("entities", new ListTag(CompoundTag.class, entities));
        compoundValue.put("offsetX", new IntTag(offsetX));
        compoundValue.put("offsetY", new IntTag(offsetY));
        compoundValue.put("offsetZ", new IntTag(offsetZ));
        compoundValue.put("version", new StringTag(version));

        SuperiorSchematic schematic = new SuperiorSchematic(new CompoundTag(compoundValue));
        schematics.put(schematicName, schematic);
        saveIntoFile(schematicName, schematic);

        if(runnable != null)
            runnable.run();
    }

    private int getCombinedId(Block block) {
        return plugin.getNMSAdapter().getCombinedId(block.getLocation());
    }

    private ItemStack getFlower(Block block){
        return plugin.getNMSAdapter().getFlowerPot(block.getLocation());
    }

    private Schematic loadFromFile(File file){
        Schematic schematic = null;

        try {
            if(!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            try (NBTInputStream reader = new NBTInputStream(new FileInputStream(file))) {
                CompoundTag compoundTag = (CompoundTag) reader.readTag();
                if (compoundTag.getValue().containsKey("version") && !compoundTag.getValue().get("version").getValue().equals(version))
                    SuperiorSkyblockPlugin.log("&cSchematic " + file.getName() + " was created in a different version, may cause issues.");
                if(compoundTag.getValue().isEmpty()) {
                    if(FAWEHook.isEnabled())
                        schematic = FAWEHook.loadSchematic(file);
                }
                else {
                    schematic = new SuperiorSchematic(compoundTag);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SuperiorSkyblockPlugin.log("&cSchematic " + file.getName() + " is invalid.");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }

        return schematic;
    }

    private void saveIntoFile(String name, SuperiorSchematic schematic){
        try {
            File file = new File(plugin.getDataFolder(), "schematics/" + name + ".schematic");

            if(file.exists())
                file.delete();

            file.getParentFile().mkdirs();
            file.createNewFile();

            NBTOutputStream writer = new NBTOutputStream(new FileOutputStream(file));

            writer.writeTag(schematic.getTag());

            writer.close();
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    private List<LivingEntity> getEntities(Location min, Location max){
        List<LivingEntity> livingEntities = new ArrayList<>();

        Chunk minChunk = min.getChunk(), maxChunk = max.getChunk();
        for(int x = minChunk.getX(); x <= maxChunk.getX(); x++){
            for(int z = minChunk.getZ(); z <= maxChunk.getZ(); z++){
                Chunk currentChunk = min.getWorld().getChunkAt(x, z);
                for(Entity entity : currentChunk.getEntities()) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && betweenLocations(entity.getLocation(), min, max))
                        livingEntities.add((LivingEntity) entity);
                }
            }
        }

        return livingEntities;
    }

    private boolean betweenLocations(Location location, Location min, Location max){
        return location.getBlockX() >= min.getBlockX() && location.getBlockX() <= max.getBlockX() &&
                location.getBlockY() >= min.getBlockY() && location.getBlockY() <= max.getBlockY() &&
                location.getBlockZ() >= min.getBlockZ() && location.getBlockZ() <= max.getBlockZ();
    }

}

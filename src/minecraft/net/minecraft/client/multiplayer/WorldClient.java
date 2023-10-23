package net.minecraft.client.multiplayer;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

import com.google.common.collect.Sets;

import appu26j.performance.CullingTargetAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecart;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.EntityFirework;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.src.Config;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveDataMemoryStorage;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import net.optifine.CustomGuis;
import net.optifine.DynamicLights;
import net.optifine.override.PlayerControllerOF;
import net.optifine.reflect.Reflector;

public class WorldClient extends World
{
    /** The packets that need to be sent to the server. */
    private NetHandlerPlayClient sendQueue;

    /** The ChunkProviderClient instance */
    private ChunkProviderClient clientChunkProvider;
    private final Set<Entity> entityList = Sets.<Entity>newHashSet();
    private final Set<Entity> entitySpawnQueue = Sets.<Entity>newHashSet();
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Set<ChunkCoordIntPair> previousActiveChunkSet = Sets.<ChunkCoordIntPair>newHashSet();
    private boolean playerUpdate = false;

    public WorldClient(NetHandlerPlayClient netHandler, WorldSettings settings, int dimension, EnumDifficulty difficulty, Profiler profilerIn)
    {
        super(new SaveHandlerMP(), new WorldInfo(settings, "MpServer"), WorldProvider.getProviderForDimension(dimension), profilerIn, true);
        this.sendQueue = netHandler;
        this.getWorldInfo().setDifficulty(difficulty);
        this.provider.registerWorld(this);
        this.setSpawnPoint(new BlockPos(8, 64, 8));
        this.chunkProvider = this.createChunkProvider();
        this.mapStorage = new SaveDataMemoryStorage();
        this.calculateInitialSkylight();
        this.calculateInitialWeather();
        Reflector.postForgeBusEvent(Reflector.WorldEvent_Load_Constructor, new Object[] {this});

        if (this.mc.playerController != null && this.mc.playerController.getClass() == PlayerControllerMP.class)
        {
            this.mc.playerController = new PlayerControllerOF(this.mc, netHandler);
            CustomGuis.setPlayerControllerOF((PlayerControllerOF)this.mc.playerController);
        }
    }

    /**
     * Runs a single tick for the world
     */
    public void tick()
    {
        super.tick();
        this.setTotalWorldTime(this.getTotalWorldTime() + 1L);

        if (this.getGameRules().getBoolean("doDaylightCycle"))
        {
            this.setWorldTime(this.getWorldTime() + 1L);
        }

        this.theProfiler.startSection("reEntryProcessing");

        for (int i = 0; i < 10 && !this.entitySpawnQueue.isEmpty(); ++i)
        {
            Entity entity = (Entity)this.entitySpawnQueue.iterator().next();
            this.entitySpawnQueue.remove(entity);

            if (!this.loadedEntityList.contains(entity))
            {
                this.spawnEntityInWorld(entity);
            }
        }

        this.theProfiler.endStartSection("chunkCache");
        this.clientChunkProvider.unloadQueuedChunks();
        this.theProfiler.endStartSection("blocks");
        this.updateBlocks();
        this.theProfiler.endSection();
    }

    /**
     * Invalidates an AABB region of blocks from the receive queue, in the event that the block has been modified
     * client-side in the intervening 80 receive ticks.
     *  
     * @param x1 X position of the block where the region begin
     * @param y1 Y position of the block where the region begin
     * @param z1 Z position of the block where the region begin
     * @param x2 X position of the block where the region end
     * @param y2 Y position of the block where the region end
     * @param z2 Z position of the block where the region end
     */
    public void invalidateBlockReceiveRegion(int x1, int y1, int z1, int x2, int y2, int z2)
    {
    }

    /**
     * Creates the chunk provider for this world. Called in the constructor. Retrieves provider from worldProvider?
     */
    protected IChunkProvider createChunkProvider()
    {
        this.clientChunkProvider = new ChunkProviderClient(this);
        return this.clientChunkProvider;
    }

    protected void updateBlocks()
    {
        super.updateBlocks();
        this.previousActiveChunkSet.retainAll(this.activeChunkSet);

        if (this.previousActiveChunkSet.size() == this.activeChunkSet.size())
        {
            this.previousActiveChunkSet.clear();
        }

        int i = 0;

        for (ChunkCoordIntPair chunkcoordintpair : this.activeChunkSet)
        {
            if (!this.previousActiveChunkSet.contains(chunkcoordintpair))
            {
                int j = chunkcoordintpair.chunkXPos * 16;
                int k = chunkcoordintpair.chunkZPos * 16;
                this.theProfiler.startSection("getChunk");
                Chunk chunk = this.getChunkFromChunkCoords(chunkcoordintpair.chunkXPos, chunkcoordintpair.chunkZPos);
                this.playMoodSoundAndCheckLight(j, k, chunk);
                this.theProfiler.endSection();
                this.previousActiveChunkSet.add(chunkcoordintpair);
                ++i;

                if (i >= 10)
                {
                    return;
                }
            }
        }
    }

    public void doPreChunk(int chuncX, int chuncZ, boolean loadChunk)
    {
        if (loadChunk)
        {
            this.clientChunkProvider.loadChunk(chuncX, chuncZ);
        }
        else
        {
            this.clientChunkProvider.unloadChunk(chuncX, chuncZ);
        }

        if (!loadChunk)
        {
            this.markBlockRangeForRenderUpdate(chuncX * 16, 0, chuncZ * 16, chuncX * 16 + 15, 256, chuncZ * 16 + 15);
        }
    }

    /**
     * Called when an entity is spawned in the world. This includes players.
     */
    public boolean spawnEntityInWorld(Entity entityIn)
    {
        boolean flag = super.spawnEntityInWorld(entityIn);
        this.entityList.add(entityIn);

        if (!flag)
        {
            this.entitySpawnQueue.add(entityIn);
        }
        else if (entityIn instanceof EntityMinecart)
        {
            this.mc.getSoundHandler().playSound(new MovingSoundMinecart((EntityMinecart)entityIn));
        }

        return flag;
    }

    /**
     * Schedule the entity for removal during the next tick. Marks the entity dead in anticipation.
     */
    public void removeEntity(Entity entityIn)
    {
        super.removeEntity(entityIn);
        this.entityList.remove(entityIn);
    }

    protected void onEntityAdded(Entity entityIn)
    {
        super.onEntityAdded(entityIn);

        if (this.entitySpawnQueue.contains(entityIn))
        {
            this.entitySpawnQueue.remove(entityIn);
        }
    }

    protected void onEntityRemoved(Entity entityIn)
    {
        super.onEntityRemoved(entityIn);
        boolean flag = false;

        if (this.entityList.contains(entityIn))
        {
            if (entityIn.isEntityAlive())
            {
                this.entitySpawnQueue.add(entityIn);
                flag = true;
            }
            else
            {
                this.entityList.remove(entityIn);
            }
        }
    }

    /**
     * Add an ID to Entity mapping to entityHashSet
     *  
     * @param entityID The ID to give to the entity to spawn
     * @param entityToSpawn The Entity to spawn in the World
     */
    public void addEntityToWorld(int entityID, Entity entityToSpawn)
    {
        Entity entity = this.getEntityByID(entityID);

        if (entity != null)
        {
            this.removeEntity(entity);
        }

        this.entityList.add(entityToSpawn);
        entityToSpawn.setEntityId(entityID);

        if (!this.spawnEntityInWorld(entityToSpawn))
        {
            this.entitySpawnQueue.add(entityToSpawn);
        }

        this.entitiesById.addKey(entityID, entityToSpawn);
    }

    /**
     * Returns the Entity with the given ID, or null if it doesn't exist in this World.
     */
    public Entity getEntityByID(int id)
    {
        return (Entity)(id == this.mc.thePlayer.getEntityId() ? this.mc.thePlayer : super.getEntityByID(id));
    }

    public Entity removeEntityFromWorld(int entityID)
    {
        Entity entity = (Entity)this.entitiesById.removeObject(entityID);

        if (entity != null)
        {
            this.entityList.remove(entity);
            this.removeEntity(entity);
        }

        return entity;
    }

    public boolean invalidateRegionAndSetBlock(BlockPos pos, IBlockState state)
    {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        this.invalidateBlockReceiveRegion(i, j, k, i, j, k);
        return super.setBlockState(pos, state, 3);
    }

    /**
     * If on MP, sends a quitting packet.
     */
    public void sendQuittingDisconnectingPacket()
    {
        this.sendQueue.getNetworkManager().closeChannel(new ChatComponentText("Quitting"));
    }

    /**
     * Updates all weather states.
     */
    protected void updateWeather()
    {
    }

    protected int getRenderDistanceChunks()
    {
        return this.mc.gameSettings.renderDistanceChunks;
    }

    public void doVoidFogParticles(int posX, int posY, int posZ)
    {
        int i = 16;
        Random random = new Random();
        ItemStack itemstack = this.mc.thePlayer.getHeldItem();
        boolean flag = this.mc.playerController.getCurrentGameType() == WorldSettings.GameType.CREATIVE && itemstack != null && Block.getBlockFromItem(itemstack.getItem()) == Blocks.barrier;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < 1000; ++j)
        {
            int k = posX + this.rand.nextInt(i) - this.rand.nextInt(i);
            int l = posY + this.rand.nextInt(i) - this.rand.nextInt(i);
            int i1 = posZ + this.rand.nextInt(i) - this.rand.nextInt(i);
            blockpos$mutableblockpos.set(k, l, i1);
            IBlockState iblockstate = this.getBlockState(blockpos$mutableblockpos);
            iblockstate.getBlock().randomDisplayTick(this, blockpos$mutableblockpos, iblockstate, random);

            if (flag && iblockstate.getBlock() == Blocks.barrier)
            {
                this.spawnParticle(EnumParticleTypes.BARRIER, (double)((float)k + 0.5F), (double)((float)l + 0.5F), (double)((float)i1 + 0.5F), 0.0D, 0.0D, 0.0D, new int[0]);
            }
        }
    }

    /**
     * also releases skins.
     */
    public void removeAllEntities()
    {
        this.loadedEntityList.removeAll(this.unloadedEntityList);

        for (int i = 0; i < this.unloadedEntityList.size(); ++i)
        {
            Entity entity = (Entity)this.unloadedEntityList.get(i);
            int j = entity.chunkCoordX;
            int k = entity.chunkCoordZ;

            if (entity.addedToChunk && this.isChunkLoaded(j, k, true))
            {
                this.getChunkFromChunkCoords(j, k).removeEntity(entity);
            }
        }

        for (int l = 0; l < this.unloadedEntityList.size(); ++l)
        {
            this.onEntityRemoved((Entity)this.unloadedEntityList.get(l));
        }

        this.unloadedEntityList.clear();

        for (int i1 = 0; i1 < this.loadedEntityList.size(); ++i1)
        {
            Entity entity1 = (Entity)this.loadedEntityList.get(i1);

            if (entity1.ridingEntity != null)
            {
                if (!entity1.ridingEntity.isDead && entity1.ridingEntity.riddenByEntity == entity1)
                {
                    continue;
                }

                entity1.ridingEntity.riddenByEntity = null;
                entity1.ridingEntity = null;
            }

            if (entity1.isDead)
            {
                int j1 = entity1.chunkCoordX;
                int k1 = entity1.chunkCoordZ;

                if (entity1.addedToChunk && this.isChunkLoaded(j1, k1, true))
                {
                    this.getChunkFromChunkCoords(j1, k1).removeEntity(entity1);
                }

                this.loadedEntityList.remove(i1--);
                this.onEntityRemoved(entity1);
            }
        }
    }

    /**
     * Adds some basic stats of the world to the given crash report.
     */
    public CrashReportCategory addWorldInfoToCrashReport(CrashReport report)
    {
        CrashReportCategory crashreportcategory = super.addWorldInfoToCrashReport(report);
        crashreportcategory.addCrashSectionCallable("Forced entities", new Callable<String>()
        {
            public String call()
            {
                return WorldClient.this.entityList.size() + " total; " + WorldClient.this.entityList.toString();
            }
        });
        crashreportcategory.addCrashSectionCallable("Retry entities", new Callable<String>()
        {
            public String call()
            {
                return WorldClient.this.entitySpawnQueue.size() + " total; " + WorldClient.this.entitySpawnQueue.toString();
            }
        });
        crashreportcategory.addCrashSectionCallable("Server brand", new Callable<String>()
        {
            public String call() throws Exception
            {
                return WorldClient.this.mc.thePlayer.getClientBrand();
            }
        });
        crashreportcategory.addCrashSectionCallable("Server type", new Callable<String>()
        {
            public String call() throws Exception
            {
                return WorldClient.this.mc.getIntegratedServer() == null ? "Non-integrated multiplayer server" : "Integrated singleplayer server";
            }
        });
        return crashreportcategory;
    }

    /**
     * Plays a sound at the specified position.
     *  
     * @param pos The position where to play the sound
     * @param soundName The name of the sound to play
     * @param volume The volume of the sound
     * @param pitch The pitch of the sound
     * @param distanceDelay True if the sound is delayed over distance
     */
    public void playSoundAtPos(BlockPos pos, String soundName, float volume, float pitch, boolean distanceDelay)
    {
        this.playSound((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D, soundName, volume, pitch, distanceDelay);
    }

    /**
     * par8 is loudness, all pars passed to minecraftInstance.sndManager.playSound
     */
    public void playSound(double x, double y, double z, String soundName, float volume, float pitch, boolean distanceDelay)
    {
        double d0 = this.mc.getRenderViewEntity().getDistanceSq(x, y, z);
        PositionedSoundRecord positionedsoundrecord = new PositionedSoundRecord(new ResourceLocation(soundName), volume, pitch, (float)x, (float)y, (float)z);

        if (distanceDelay && d0 > 100.0D)
        {
            double d1 = Math.sqrt(d0) / 40.0D;
            this.mc.getSoundHandler().playDelayedSound(positionedsoundrecord, (int)(d1 * 20.0D));
        }
        else
        {
            this.mc.getSoundHandler().playSound(positionedsoundrecord);
        }
    }

    public void makeFireworks(double x, double y, double z, double motionX, double motionY, double motionZ, NBTTagCompound compund)
    {
        this.mc.effectRenderer.addEffect(new EntityFirework.StarterFX(this, x, y, z, motionX, motionY, motionZ, this.mc.effectRenderer, compund));
    }

    public void setWorldScoreboard(Scoreboard scoreboardIn)
    {
        this.worldScoreboard = scoreboardIn;
    }

    /**
     * Sets the world time.
     */
    public void setWorldTime(long time)
    {
        if (time < 0L)
        {
            time = -time;
            this.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        }
        else
        {
            this.getGameRules().setOrCreateGameRule("doDaylightCycle", "true");
        }

        super.setWorldTime(time);
    }

    public int getCombinedLight(BlockPos pos, int lightValue)
    {
        int i = super.getCombinedLight(pos, lightValue);

        if (Config.isDynamicLights())
        {
            i = DynamicLights.getCombinedLight(pos, i);
        }

        return i;
    }

    /**
     * Sets the block state at a given location. Flag 1 will cause a block update. Flag 2 will send the change to
     * clients (you almost always want this). Flag 4 prevents the block from being re-rendered, if this is a client
     * world. Flags can be added together.
     */
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags)
    {
        this.playerUpdate = this.isPlayerActing();
        boolean flag = super.setBlockState(pos, newState, flags);
        this.playerUpdate = false;
        return flag;
    }

    private boolean isPlayerActing()
    {
        if (this.mc.playerController instanceof PlayerControllerOF)
        {
            PlayerControllerOF playercontrollerof = (PlayerControllerOF)this.mc.playerController;
            return playercontrollerof.isActing();
        }
        else
        {
            return false;
        }
    }

    public boolean isPlayerUpdate()
    {
        return this.playerUpdate;
    }
    

    public boolean isVisible(CullingTargetAccessor accessor)
    {
        if (accessor == null || this.mc.thePlayer.ticksExisted < 20)
        {
            return true;
        }
        
        boolean evenTick = this.mc.thePlayer.ticksExisted % 2 == 0;
        
        if (accessor.isLastCheckEvenTick() == evenTick)
        {
            return accessor.isLastCullingVisible();
        }
        
        boolean lastCullingVisible = accessor.isLastCullingVisible();
        long timePassedSinceLastCheck = System.currentTimeMillis() - accessor.getLastTimeChecked();
        
        if (lastCullingVisible && timePassedSinceLastCheck < 2000L)
        {
            return true;
        }
        
        boolean visible = this.isVisible(accessor.getMinX(), accessor.getMinY(), accessor.getMinZ(), accessor.getMaxX(), accessor.getMaxY(), accessor.getMaxZ());
        accessor.setLastCullingVisible(visible, evenTick);
        return visible;
    }

    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        if (this.mc.thePlayer == null || this.mc.thePlayer.ticksExisted < 20)
        {
            return true;
        }
        
        Vec3 cameraPosition = new Vec3(this.mc.thePlayer.posX, this.mc.thePlayer.posY + this.mc.thePlayer.getEyeHeight(), this.mc.thePlayer.posZ);
        return this.isVisible(minX, minY, minZ, maxX, maxY, maxZ, cameraPosition.xCoord, cameraPosition.yCoord, cameraPosition.zCoord);
    }

    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double cameraX, double cameraY, double cameraZ)
    {
        if (this.mc.thePlayer == null || this.mc.thePlayer.ticksExisted < 20)
        {
            return true;
        }
        
        double distanceRay1X = minX - cameraX;
        double distanceRay1Y = minY - cameraY;
        double distanceRay1Z = minZ - cameraZ;
        double ray1X = cameraX;
        double ray1Y = cameraY;
        double ray1Z = cameraZ;
        double distanceRay1 = Math.sqrt(distanceRay1X * distanceRay1X + distanceRay1Y * distanceRay1Y + distanceRay1Z * distanceRay1Z);
        double step1X = distanceRay1X / distanceRay1;
        double step1Y = distanceRay1Y / distanceRay1;
        double step1Z = distanceRay1Z / distanceRay1;
        boolean ray1XPositive = step1X > 0.0;
        boolean ray1YPositive = step1Y > 0.0;
        boolean ray1ZPositive = step1Z > 0.0;
        double distanceRay2X = maxX - cameraX;
        double distanceRay2Y = maxY - cameraY;
        double distanceRay2Z = maxZ - cameraZ;
        double ray2X = cameraX;
        double ray2Y = cameraY;
        double ray2Z = cameraZ;
        double distanceRay2 = Math.sqrt(distanceRay2X * distanceRay2X + distanceRay2Y * distanceRay2Y + distanceRay2Z * distanceRay2Z);
        double step2X = distanceRay2X / distanceRay2;
        double step2Y = distanceRay2Y / distanceRay2;
        double step2Z = distanceRay2Z / distanceRay2;
        boolean ray2XPositive = step2X > 0.0;
        boolean ray2YPositive = step2Y > 0.0;
        boolean ray2ZPositive = step2Z > 0.0;
        double distanceRay3X = minX - cameraX;
        double distanceRay3Y = maxY - cameraY;
        double distanceRay3Z = minZ - cameraZ;
        double ray3X = cameraX;
        double ray3Y = cameraY;
        double ray3Z = cameraZ;
        double distanceRay3 = Math.sqrt(distanceRay3X * distanceRay3X + distanceRay3Y * distanceRay3Y + distanceRay3Z * distanceRay3Z);
        double step3X = distanceRay3X / distanceRay3;
        double step3Y = distanceRay3Y / distanceRay3;
        double step3Z = distanceRay3Z / distanceRay3;
        boolean ray3XPositive = step3X > 0.0;
        boolean ray3YPositive = step3Y > 0.0;
        boolean ray3ZPositive = step3Z > 0.0;
        double distanceRay4X = maxX - cameraX;
        double distanceRay4Y = minY - cameraY;
        double distanceRay4Z = maxZ - cameraZ;
        double ray4X = cameraX;
        double ray4Y = cameraY;
        double ray4Z = cameraZ;
        double distanceRay4 = Math.sqrt(distanceRay4X * distanceRay4X + distanceRay4Y * distanceRay4Y + distanceRay4Z * distanceRay4Z);
        double step4X = distanceRay4X / distanceRay4;
        double step4Y = distanceRay4Y / distanceRay4;
        double step4Z = distanceRay4Z / distanceRay4;
        boolean ray4XPositive = step4X > 0.0;
        boolean ray4YPositive = step4Y > 0.0;
        boolean ray4ZPositive = step4Z > 0.0;
        double maxDistance = Math.max(Math.max(Math.abs(distanceRay1), Math.abs(distanceRay2)), Math.max(Math.abs(distanceRay3), Math.abs(distanceRay4)));
        boolean ray1Hit = false;
        boolean ray2Hit = false;
        boolean ray3Hit = false;
        boolean ray4Hit = false;
        boolean ray1Free = false;
        boolean ray2Free = false;
        boolean ray3Free = false;
        boolean ray4Free = false;
        int i = 0;
        
        while (i < Math.ceil(maxDistance))
        {
            if (this.isFullBlockAt(MathHelper.floor_double(ray1X), MathHelper.floor_double(ray1Y), MathHelper.floor_double((ray1Z))))
            {
                ray1Hit = ray1Free;
            }
            
            else
            {
                ray1Free = true;
            }
            
            if (this.isFullBlockAt(MathHelper.floor_double(ray2X), MathHelper.floor_double(ray2Y), MathHelper.floor_double(ray2Z)))
            {
                ray2Hit = ray2Free;
            }
            
            else
            {
                ray2Free = true;
            }
            
            if (this.isFullBlockAt(MathHelper.floor_double(ray3X), MathHelper.floor_double(ray3Y), MathHelper.floor_double(ray3Z)))
            {
                ray3Hit = ray3Free;
            }
            
            else
            {
                ray3Free = true;
            }
            
            if (this.isFullBlockAt(MathHelper.floor_double(ray4X), MathHelper.floor_double(ray4Y), MathHelper.floor_double(ray4Z)))
            {
                ray4Hit = ray4Free;
            }
            
            else
            {
                ray4Free = true;
            }
            
            if (ray1Hit && ray2Hit && ray3Hit && ray4Hit)
            {
                return false;
            }
            
            ray1X = ray1XPositive ? Math.min(ray1X + step1X, minX) : Math.max(ray1X + step1X, minX);
            ray1Y = ray1YPositive ? Math.min(ray1Y + step1Y, minY) : Math.max(ray1Y + step1Y, minY);
            ray1Z = ray1ZPositive ? Math.min(ray1Z + step1Z, minZ) : Math.max(ray1Z + step1Z, minZ);
            ray2X = ray2XPositive ? Math.min(ray2X + step2X, maxX) : Math.max(ray2X + step2X, maxX);
            ray2Y = ray2YPositive ? Math.min(ray2Y + step2Y, maxY) : Math.max(ray2Y + step2Y, maxY);
            ray2Z = ray2ZPositive ? Math.min(ray2Z + step2Z, maxZ) : Math.max(ray2Z + step2Z, maxZ);
            ray3X = ray3XPositive ? Math.min(ray3X + step3X, minX) : Math.max(ray3X + step3X, minX);
            ray3Y = ray3YPositive ? Math.min(ray3Y + step3Y, maxY) : Math.max(ray3Y + step3Y, minY);
            ray3Z = ray3ZPositive ? Math.min(ray3Z + step3Z, minZ) : Math.max(ray3Z + step3Z, minZ);
            ray4X = ray4XPositive ? Math.min(ray4X + step4X, maxX) : Math.max(ray4X + step4X, maxX);
            ray4Y = ray4YPositive ? Math.min(ray4Y + step4Y, minY) : Math.max(ray4Y + step4Y, minY);
            ray4Z = ray4ZPositive ? Math.min(ray4Z + step4Z, maxZ) : Math.max(ray4Z + step4Z, maxZ);
            ++i;
        }
        
        return true;
    }

    protected boolean isFullBlockAt(int x, int y, int z)
    {
        if (this.mc.thePlayer == null || this.mc.thePlayer.ticksExisted < 5)
        {
            return true;
        }
        
        BlockState blockState = new BlockState(this.getBlockState(new BlockPos(x, y, z)).getBlock());
        return isFullBlock(blockState);
    }
    
    protected boolean isFullBlock(BlockState blockState)
    {
        if (this.mc.thePlayer == null || this.mc.thePlayer.ticksExisted < 5)
        {
            return true;
        }
        
        return blockState.getBlock().isFullBlock();
    }
}

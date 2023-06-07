package com.mattymatty.carpet_reducedpackets.utils.holders;

import com.mattymatty.carpet_reducedpackets.ReducedPackets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EntityPacketHolder {
    public static final Map<Class<?>, Function<Packet<?>, Integer>> HANDLED_PACKETS = Map.ofEntries(
            Map.entry(EntitySpawnS2CPacket.class, (Packet<?> packet) -> ((EntitySpawnS2CPacket) packet).getId()),
            Map.entry(EntitiesDestroyS2CPacket.class, (Packet<?> packet) -> -1),
            Map.entry(EntityPositionS2CPacket.class, (Packet<?> packet) -> ((EntityPositionS2CPacket) packet).getId()),
            Map.entry(EntityS2CPacket.RotateAndMoveRelative.class, (Packet<?> packet) -> ((EntityS2CPacket) packet).id),
            Map.entry(EntityS2CPacket.Rotate.class, (Packet<?> packet) -> ((EntityS2CPacket) packet).id),
            Map.entry(EntityS2CPacket.MoveRelative.class, (Packet<?> packet) -> ((EntityS2CPacket) packet).id),
            Map.entry(EntitySetHeadYawS2CPacket.class, (Packet<?> packet) -> ((EntitySetHeadYawS2CPacket) packet).entity),
            Map.entry(EntityAttributesS2CPacket.class, (Packet<?> packet) -> ((EntityAttributesS2CPacket) packet).getEntityId()),
            Map.entry(EntityStatusS2CPacket.class, (Packet<?> packet) -> ((EntityStatusS2CPacket) packet).id),
            Map.entry(EntityVelocityUpdateS2CPacket.class, (Packet<?> packet) -> ((EntityVelocityUpdateS2CPacket) packet).getId()),
            Map.entry(EntityPassengersSetS2CPacket.class, (Packet<?> packet) -> ((EntityPassengersSetS2CPacket) packet).getId()),
            Map.entry(EntityTrackerUpdateS2CPacket.class, (Packet<?> packet) -> ((EntityTrackerUpdateS2CPacket) packet).id()),
            Map.entry(EntityEquipmentUpdateS2CPacket.class, (Packet<?> packet) -> ((EntityEquipmentUpdateS2CPacket) packet).getId()),
            Map.entry(EntityAttachS2CPacket.class, (Packet<?> packet) -> ((EntityAttachS2CPacket) packet).getHoldingEntityId()),
            Map.entry(EntityAnimationS2CPacket.class, (Packet<?> packet) -> ((EntityAnimationS2CPacket) packet).getId()),
            Map.entry(PlaySoundFromEntityS2CPacket.class, (Packet<?> packet) -> ((PlaySoundFromEntityS2CPacket) packet).getEntityId())
    );
    public final int ID;
    public final Int2ObjectMap<EntityAnimationS2CPacket> animations = new Int2ObjectLinkedOpenHashMap<>();
    public final Map<Identifier, PlaySoundFromEntityS2CPacket> sounds = new LinkedHashMap<>();
    public EntityAttachS2CPacket leash;
    public boolean exists = true;
    public EntitySpawnS2CPacket spawnPacket;
    public EntitiesDestroyS2CPacket destroyPacket;
    public EntityPositionS2CPacket absolutePosition;
    public EntityS2CPacket relativePosition;
    public EntitySetHeadYawS2CPacket head_yaw;
    public EntityAttributesS2CPacket attributes;
    public EntityStatusS2CPacket status;
    public EntityVelocityUpdateS2CPacket velocity;
    public EntityPassengersSetS2CPacket passengers;
    public EntityTrackerUpdateS2CPacket dataTracker;
    public EntityEquipmentUpdateS2CPacket equipment;

    public EntityPacketHolder(int ID) {
        this.ID = ID;
    }

    public static boolean handlePacket(Packet<?> packet, RegistryKey<World> world_key, Map<Integer, EntityPacketHolder> entityMap) {
        World world = ReducedPackets.getMs().getWorld(world_key);
        if (world == null)
            return false;
        int entityId = HANDLED_PACKETS.getOrDefault(packet.getClass(), (p) -> -1).apply(packet);
        Entity entity = null;
        try {
            entity = world.getEntityById(entityId);
        } catch (IndexOutOfBoundsException ignored) {
        }
        if (entity != null) {
            //do not throttle player packets or vehicles ridden by players
            if (entity instanceof PlayerEntity || entity.getPassengerList().stream().anyMatch(e -> e instanceof PlayerEntity))
                return false;
        }

        if (packet instanceof EntitiesDestroyS2CPacket entitiesDestroyS2CPacket) {
            for (int id : entitiesDestroyS2CPacket.getEntityIds()) {
                EntityPacketHolder holder = new EntityPacketHolder(id);
                holder.exists = false;
                holder.destroyPacket = entitiesDestroyS2CPacket;
                entityMap.put(id, holder);
            }
            return true;
        }

        //if we get an invalid entity
        if (entity == null)
            return false;

        EntityPacketHolder holder = entityMap.computeIfAbsent(entityId, EntityPacketHolder::new);

        if (packet instanceof EntitySpawnS2CPacket spawnS2CPacket) {
            holder.spawnPacket = spawnS2CPacket;
            holder.exists = true;
            return true;
        }

        if (packet instanceof EntityPositionS2CPacket positionS2CPacket) {
            holder.absolutePosition = positionS2CPacket;
            holder.relativePosition = null;
            return true;
        }

        if (packet instanceof EntityS2CPacket entityS2CPacket) {
            EntityS2CPacket relative_packet = holder.relativePosition;
            if (relative_packet == null){
                holder.relativePosition = entityS2CPacket;
            }else{
                if (relative_packet instanceof EntityS2CPacket.Rotate rotate) {
                    if (packet instanceof EntityS2CPacket.Rotate || packet instanceof EntityS2CPacket.RotateAndMoveRelative) {
                        holder.relativePosition = entityS2CPacket;
                    } else if (packet instanceof EntityS2CPacket.MoveRelative move) {
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), rotate.getYaw(), rotate.getPitch(), move.isOnGround());
                    }
                } else if (relative_packet instanceof EntityS2CPacket.MoveRelative move) {
                    if (packet instanceof EntityS2CPacket.Rotate rotate){
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), rotate.getYaw(), rotate.getPitch(), move.isOnGround());
                    }else if (packet instanceof EntityS2CPacket.MoveRelative move2){
                        Vec3d vect = EntityS2CPacket.decodePacketCoordinates(move.getDeltaX(),move.getDeltaY(),move.getDeltaZ());
                        Vec3d vect2 = EntityS2CPacket.decodePacketCoordinates(move2.getDeltaX(),move2.getDeltaY(),move2.getDeltaZ());
                        Vec3d vect3 = vect.add(vect2);
                        holder.relativePosition = new EntityS2CPacket.MoveRelative(entityId, (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.x)),(short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.y)),(short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.z)), move2.isOnGround());
                    }else if (packet instanceof EntityS2CPacket.RotateAndMoveRelative rotateAndMove){
                        Vec3d vect = EntityS2CPacket.decodePacketCoordinates(move.getDeltaX(),move.getDeltaY(),move.getDeltaZ());
                        Vec3d vect2 = EntityS2CPacket.decodePacketCoordinates(rotateAndMove.getDeltaX(),rotateAndMove.getDeltaY(),rotateAndMove.getDeltaZ());
                        Vec3d vect3 = vect.add(vect2);
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.x)),(short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.y)),(short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.z)),rotateAndMove.getYaw(),rotateAndMove.getPitch(), rotateAndMove.isOnGround());
                    }
                } else if (relative_packet instanceof EntityS2CPacket.RotateAndMoveRelative rotateAndMove) {
                    if (packet instanceof EntityS2CPacket.Rotate rotate){
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, rotateAndMove.getDeltaX(), rotateAndMove.getDeltaY(), rotateAndMove.getDeltaZ(), rotate.getYaw(), rotate.getPitch(), rotateAndMove.isOnGround());
                    }else if (packet instanceof EntityS2CPacket.MoveRelative move){
                        Vec3d vect = EntityS2CPacket.decodePacketCoordinates(move.getDeltaX(),move.getDeltaY(),move.getDeltaZ());
                        Vec3d vect2 = EntityS2CPacket.decodePacketCoordinates(rotateAndMove.getDeltaX(),rotateAndMove.getDeltaY(),rotateAndMove.getDeltaZ());
                        Vec3d vect3 = vect.add(vect2);
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.x)), (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.y)), (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.z)), rotateAndMove.getYaw(), rotateAndMove.getPitch(), move.isOnGround());
                    }else if (packet instanceof EntityS2CPacket.RotateAndMoveRelative rotateAndMove2){
                        Vec3d vect = EntityS2CPacket.decodePacketCoordinates(rotateAndMove.getDeltaX(),rotateAndMove.getDeltaY(),rotateAndMove.getDeltaZ());
                        Vec3d vect2 = EntityS2CPacket.decodePacketCoordinates(rotateAndMove2.getDeltaX(),rotateAndMove2.getDeltaY(),rotateAndMove2.getDeltaZ());
                        Vec3d vect3 = vect.add(vect2);
                        holder.relativePosition = new EntityS2CPacket.RotateAndMoveRelative(entityId, (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.x)), (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.y)), (short)((int)EntityS2CPacket.encodePacketCoordinate(vect3.z)), rotateAndMove2.getYaw(), rotateAndMove2.getPitch(), rotateAndMove2.isOnGround());
                    }
                }
            }
            //holder.absolutePosition = new EntityPositionS2CPacket(entity);
            return true;
        }

        if (packet instanceof EntitySetHeadYawS2CPacket headYawS2CPacket) {
            holder.head_yaw = headYawS2CPacket;
            return true;
        }

        if (packet instanceof EntityAttributesS2CPacket attributesS2CPacket) {
            EntityAttributesS2CPacket old = holder.attributes;
            if (old == null) {
                holder.attributes = attributesS2CPacket;
            } else {
                Map<EntityAttribute, EntityAttributeInstance> old_map = map_attribute_packet(old);
                Map<EntityAttribute, EntityAttributeInstance> new_map = map_attribute_packet(attributesS2CPacket);

                old_map.forEach((k, v) -> new_map.merge(k, v, (v2, v1) -> {
                    EntityAttributeInstance ret = new EntityAttributeInstance(k, a -> {
                    });
                    for (EntityAttributeModifier modifier : v1.getModifiers()) {
                        ret.addTemporaryModifier(modifier);
                    }
                    for (EntityAttributeModifier modifier : v2.getModifiers()) {
                        ret.addTemporaryModifier(modifier);
                    }
                    ret.setBaseValue(v2.getBaseValue());
                    return ret;
                }));
                holder.attributes = new EntityAttributesS2CPacket(entityId, new_map.values());
                return true;
            }
        }

        if (packet instanceof EntityStatusS2CPacket statusS2CPacket) {
            holder.status = statusS2CPacket;
            return true;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocityUpdateS2CPacket) {
            holder.velocity = velocityUpdateS2CPacket;
            return true;
        }

        if (packet instanceof EntityPassengersSetS2CPacket passengersSetS2CPacket) {
            holder.passengers = passengersSetS2CPacket;
            return true;
        }

        if (packet instanceof EntityTrackerUpdateS2CPacket trackerUpdateS2CPacket) {
            if (holder.dataTracker == null) {
                holder.dataTracker = trackerUpdateS2CPacket;
            } else {
                holder.dataTracker = new EntityTrackerUpdateS2CPacket(entityId, entity.getDataTracker(), true);
            }
            return true;
        }

        if (packet instanceof EntityEquipmentUpdateS2CPacket equipmentUpdateS2CPacket) {
            if (holder.equipment == null) {
                holder.equipment = equipmentUpdateS2CPacket;
            } else {
                Map<EquipmentSlot, ItemStack> old_map = holder.equipment.getEquipmentList().stream().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
                Map<EquipmentSlot, ItemStack> new_map = equipmentUpdateS2CPacket.getEquipmentList().stream().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
                old_map.forEach((k, v) -> new_map.merge(k, v, (v2, v1) -> v2));
                holder.equipment = new EntityEquipmentUpdateS2CPacket(entityId, new_map.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue())).collect(Collectors.toList()));
            }
            return true;
        }

        if (packet instanceof EntityAnimationS2CPacket animationS2CPacket) {
            holder.animations.put(animationS2CPacket.getAnimationId(), animationS2CPacket);
            return true;
        }

        if (packet instanceof EntityAttachS2CPacket attachS2CPacket) {
            holder.leash = attachS2CPacket;
            return true;
        }

        if (packet instanceof PlaySoundFromEntityS2CPacket entitySoundS2CPacket) {
            holder.sounds.put(entitySoundS2CPacket.getSound().getId(), entitySoundS2CPacket);
            return true;
        }

        return false;
    }

    public static void flushPackets(Map<Integer, EntityPacketHolder> entityMap, Consumer<Packet<?>> sender) {
        //remove dummy entity
        entityMap.remove(-1);
        //remove all dead entities
        IntList entities_to_destory = entityMap.values().stream()
                .filter(holder -> holder.destroyPacket != null)
                .map(EntityPacketHolder::getID)
                .collect(Collectors.toCollection(IntArrayList::new));
        sender.accept(new EntitiesDestroyS2CPacket(entities_to_destory));
        //get all non-dead entities
        List<EntityPacketHolder> alive_entities = entityMap.values().stream()
                .filter(EntityPacketHolder::exists).toList();
        //spawn all new entities
        alive_entities.stream()
                .map(EntityPacketHolder::getSpawnPacket)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all absolute positions
        alive_entities.stream()
                .map(EntityPacketHolder::getAbsolutePosition)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all relative positions
        alive_entities.stream().filter(holder->holder.relativePosition!=null)
                .map(EntityPacketHolder::getRelativePosition)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all head positions
        alive_entities.stream()
                .map(EntityPacketHolder::getHead_yaw)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all velocities
        alive_entities.stream()
                .map(EntityPacketHolder::getVelocity)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update passengers
        alive_entities.stream()
                .map(EntityPacketHolder::getPassengers)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all dataTrackers
        alive_entities.stream()
                .map(EntityPacketHolder::getDataTracker)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all attributes
        alive_entities.stream()
                .map(EntityPacketHolder::getAttributes)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all statuses
        alive_entities.stream()
                .map(EntityPacketHolder::getStatus)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all equipments
        alive_entities.stream()
                .map(EntityPacketHolder::getEquipment)
                .filter(Objects::nonNull)
                .forEach(sender);
        //update all leads
        alive_entities.stream()
                .map(EntityPacketHolder::getLeash)
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all animations
        alive_entities.stream().map(EntityPacketHolder::getAnimations)
                .filter(Objects::nonNull)
                .map(Int2ObjectMap::values)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .forEach(sender);
        //send all sounds even from dead mobs
        entityMap.values().stream().map(EntityPacketHolder::getSounds)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .forEach(sender);
        entityMap.clear();
    }

    private static Map<EntityAttribute, EntityAttributeInstance> map_attribute_packet(EntityAttributesS2CPacket attributesS2CPacket) {
        return attributesS2CPacket.getEntries().stream().collect(Collectors.toMap(EntityAttributesS2CPacket.Entry::getId, e -> {
            EntityAttributeInstance ret = new EntityAttributeInstance(e.getId(), a -> {
            });
            for (EntityAttributeModifier modifier : e.getModifiers()) {
                ret.addTemporaryModifier(modifier);
            }
            ret.setBaseValue(e.getBaseValue());
            return ret;
        }));
    }

    public int getID() {
        return ID;
    }

    public EntityAttachS2CPacket getLeash() {
        return leash;
    }

    public Int2ObjectMap<EntityAnimationS2CPacket> getAnimations() {
        return animations;
    }

    public boolean exists() {
        return exists;
    }

    public EntitySpawnS2CPacket getSpawnPacket() {
        return spawnPacket;
    }

    public EntitiesDestroyS2CPacket getDestroyPacket() {
        return destroyPacket;
    }

    public EntityPositionS2CPacket getAbsolutePosition() {
        return absolutePosition;
    }

    public EntityS2CPacket getRelativePosition() {
        return relativePosition;
    }

    public EntitySetHeadYawS2CPacket getHead_yaw() {
        return head_yaw;
    }

    public EntityAttributesS2CPacket getAttributes() {
        return attributes;
    }

    public EntityStatusS2CPacket getStatus() {
        return status;
    }

    public EntityVelocityUpdateS2CPacket getVelocity() {
        return velocity;
    }

    public EntityPassengersSetS2CPacket getPassengers() {
        return passengers;
    }

    public EntityTrackerUpdateS2CPacket getDataTracker() {
        return dataTracker;
    }

    public EntityEquipmentUpdateS2CPacket getEquipment() {
        return equipment;
    }

    public Collection<PlaySoundFromEntityS2CPacket> getSounds() {
        return sounds.values();
    }
}

package io.github.nenwam.villagerhuts.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.util.RandomSource;

/**
 * How a worker looks: one of these, picked at random when it's hired, along with a name that suits it.
 * Farmers get farmers' clothes; fighters (warriors and rangers) are all Sikhs, in the dress of Sikh warriors.
 * The textures are at textures/entity/worker/<id>.png (drawn by tools/make_worker_looks.py; to add a look,
 * draw it there and add it here, at the end: workers are synced by position in this list).
 */
public enum WorkerLook {
    PAGRI_KURTA("pagri_kurta", Kind.FARMER, WorkerNames.MEN, ""),         // white kurta and dhoti, saffron pagri
    GAMCHA_LUNGI("gamcha_lungi", Kind.FARMER, WorkerNames.MEN, ""),       // vest and checked lungi, gamcha tied round the head
    DASTAR("dastar", Kind.FARMER, WorkerNames.SIKH_MEN, ""),              // sky-blue kurta, navy dastar, beard and kara
    ELDER("elder", Kind.FARMER, WorkerNames.MEN, ""),                     // older man, rust kurta, gray hair
    SAREE_GREEN("saree_green", Kind.FARMER, WorkerNames.WOMEN, ""),       // green saree, pallu over the head
    SAREE_PINK("saree_pink", Kind.FARMER, WorkerNames.WOMEN, ""),         // pink saree, braid
    SALWAR_KAMEEZ("salwar_kameez", Kind.FARMER, WorkerNames.WOMEN, ""),   // yellow kameez, red dupatta
    SAREE_BLUE("saree_blue", Kind.FARMER, WorkerNames.WOMEN, ""),         // indigo saree, pallu over the head
    NIHANG_BLUE("nihang_blue", Kind.FIGHTER, WorkerNames.SIKH_MEN, "Singh"),     // Nihang: blue chola, saffron sash, tall blue dumalla with a steel chakkar
    NIHANG_ELDER("nihang_elder", Kind.FIGHTER, WorkerNames.SIKH_MEN, "Singh"),   // older Nihang: saffron chola, navy dumalla, white beard
    SINGH_KESRI("singh_kesri", Kind.FIGHTER, WorkerNames.SIKH_MEN, "Singh"),     // white kurta, saffron dastar, black gatra strap across the chest
    KAUR_BLUE("kaur_blue", Kind.FIGHTER, WorkerNames.SIKH_WOMEN, "Kaur"),        // blue chola, saffron dastar and sash, gatra strap
    KAUR_KESRI("kaur_kesri", Kind.FIGHTER, WorkerNames.SIKH_WOMEN, "Kaur");      // saffron chola, navy dastar, blue sash

    /** Which kind of worker wears it. */
    public enum Kind { FARMER, FIGHTER }

    private final String id;
    private final Kind kind;
    private final List<String> names;
    private final String surname;

    WorkerLook(String id, Kind kind, List<String> names, String surname) {
        this.id = id;
        this.kind = kind;
        this.names = names;
        this.surname = surname;
    }

    public String id() {
        return this.id;
    }

    public Kind kind() {
        return this.kind;
    }

    /** A name for a worker with this look, avoiding the ones in `taken` while there are others. */
    public String pickName(Set<String> taken, RandomSource random) {
        return WorkerNames.pick(this.names, this.surname, taken, random);
    }

    public static WorkerLook random(Kind kind, RandomSource random) {
        List<WorkerLook> looks = ofKind(kind);
        return looks.get(random.nextInt(looks.size()));
    }

    /** For workers saved before looks existed (all farmers): always the same look for the same worker. */
    public static WorkerLook forOldWorker(UUID worker) {
        List<WorkerLook> looks = ofKind(Kind.FARMER);
        return looks.get(Math.floorMod(worker.hashCode(), looks.size()));
    }

    public static List<WorkerLook> ofKind(Kind kind) {
        return Arrays.stream(values()).filter(look -> look.kind == kind).toList();
    }

    public static WorkerLook byId(String id) {
        for (WorkerLook look : values()) {
            if (look.id.equals(id)) {
                return look;
            }
        }
        return PAGRI_KURTA;
    }

    public static WorkerLook byOrdinal(int ordinal) {
        WorkerLook[] looks = values();
        return looks[Math.floorMod(ordinal, looks.length)];
    }
}

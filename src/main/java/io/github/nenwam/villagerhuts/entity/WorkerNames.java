package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.util.RandomSource;

/**
 * First names for new workers, from all over India (north and south, Hindu, Muslim, Sikh and Christian).
 * Which list a worker's name comes from depends on its look (see WorkerLook).
 */
public final class WorkerNames {
    static final List<String> MEN = List.of(
            "Ramesh", "Suresh", "Mahesh", "Ganesh", "Raju", "Mohan", "Gopal", "Ravi", "Vijay", "Sanjay",
            "Anil", "Sunil", "Prakash", "Dinesh", "Manoj", "Rajesh", "Mukesh", "Arjun", "Karthik", "Murugan",
            "Senthil", "Venkatesh", "Srinivas", "Naveen", "Anand", "Deepak", "Harish", "Lakshman", "Shankar", "Hari",
            "Balu", "Santosh", "Pradeep", "Vitthal", "Ramdas", "Bhola", "Imran", "Salim", "Irfan", "Rafiq",
            "Yusuf", "Abdul", "Joseph", "Thomas", "Mathew", "Sourav", "Bikash", "Debashish", "Kishore", "Narayan");

    static final List<String> SIKH_MEN = List.of(
            "Gurpreet", "Harjit", "Jaswinder", "Manpreet", "Balwinder", "Sukhdev", "Amarjit", "Kulwinder",
            "Paramjit", "Ranjit", "Harbhajan", "Gurdev", "Baldev", "Daljit", "Gurmeet", "Harpal",
            "Inderjit", "Jagdeep", "Karamjit", "Lakhwinder", "Navdeep", "Satnam", "Tejinder", "Hardeep");

    /** Sikh women's first names (fighters' names end in Kaur, the name every Sikh woman shares). */
    static final List<String> SIKH_WOMEN = List.of(
            "Harleen", "Jaspreet", "Simran", "Gurleen", "Manpreet", "Navneet", "Amandeep", "Rajwinder",
            "Sukhpreet", "Kirandeep", "Baljit", "Harpreet", "Jasleen", "Ravneet", "Satwant", "Daljeet",
            "Inderjeet", "Prabhjot", "Mandeep", "Harjot", "Rupinder", "Sandeep", "Gurmeet", "Taranjit");

    static final List<String> WOMEN = List.of(
            "Lakshmi", "Sita", "Gita", "Radha", "Parvati", "Savitri", "Kamala", "Shanti", "Asha", "Usha",
            "Meena", "Kavita", "Sunita", "Anita", "Rekha", "Pooja", "Priya", "Anjali", "Divya", "Deepa",
            "Nisha", "Lata", "Sarita", "Malini", "Padma", "Revathi", "Selvi", "Vasanthi", "Kavya", "Meenakshi",
            "Bhavna", "Sneha", "Rani", "Durga", "Champa", "Basanti", "Mamta", "Pushpa", "Ayesha", "Fatima",
            "Zainab", "Nazia", "Rukhsana", "Mary", "Annamma", "Simran", "Harleen", "Jaspreet", "Rupa", "Sushila");

    /**
     * A name from the list, one not in `taken` (e.g. the owner's other workers' names) while there are any left.
     * @param surname added after the first name ("Singh", "Kaur"), or empty for none
     */
    static String pick(List<String> names, String surname, Set<String> taken, RandomSource random) {
        List<String> all = names.stream().map(name -> surname.isEmpty() ? name : name + " " + surname).toList();
        List<String> free = new ArrayList<>(all);
        free.removeAll(taken);
        List<String> from = free.isEmpty() ? all : free;
        return from.get(random.nextInt(from.size()));
    }

    /** Whether the name is on any of the lists, with or without a Sikh surname (the tests use this). */
    public static boolean isKnown(String name) {
        String first = name.endsWith(" Singh") || name.endsWith(" Kaur") ? name.substring(0, name.lastIndexOf(' ')) : name;
        return MEN.contains(first) || SIKH_MEN.contains(first) || WOMEN.contains(first) || SIKH_WOMEN.contains(first);
    }

    /** Whether the name is a Sikh fighter's: a Sikh first name, then Singh or Kaur (the tests use this). */
    public static boolean isSikhFighterName(String name) {
        return name.endsWith(" Singh") && SIKH_MEN.contains(name.substring(0, name.length() - " Singh".length()))
                || name.endsWith(" Kaur") && SIKH_WOMEN.contains(name.substring(0, name.length() - " Kaur".length()));
    }

    private WorkerNames() {}
}

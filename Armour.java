package abd;

public enum Armour {

	//Bronze(),
	//Iron(),
	//Steel(),
	//Black(),
	Mithril(1159,1071,1121,2454),
	Adamant(1123, 1073, 1161, 2455);
	//Rune();
	
	public int helm, body, legs, npc;
	private Armour(int helm, int body, int legs, int npc){
		this.helm = helm;
		this.body = body;
		this.legs = legs;
		this.npc = npc;
	}
	public static Armour getArmour(String name){
		for(Armour a : Armour.values())
			if(a.name().equalsIgnoreCase(name))
				return a;
		return null;
	}
}

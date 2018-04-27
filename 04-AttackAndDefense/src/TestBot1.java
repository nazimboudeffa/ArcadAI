import java.util.ArrayList;
import java.util.List;
import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;

//Units	
	private Unit bBunker= null;
	private Unit bSupply= null;
	private Unit bBarrack= null;
	
//Orders	
	private boolean defenseOK = false;
	private boolean gogogo = false;
	
//Positions
		Position positionToAttack = null;
	
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        //System.out.println("New unit discovered " + unit.getType());
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }

    }

    @Override
    public void onFrame() {
		List<Unit> workers = new ArrayList<>();
		List<Unit> builders = new ArrayList<>();
		
		List<Unit> marines = new ArrayList<>();
		List<Unit> mDefenders = new ArrayList<>();
		List<Unit> mAttackers = new ArrayList<>();
		
		List<Unit> supplys = new ArrayList<>();
		List<Unit> barracks = new ArrayList<>();
		List<Unit> bunkers = new ArrayList<>();
		
		int maxBunkers = 3;
		int maxDefenders = 4 * maxBunkers;
		int minAttackers = 11;

        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

        StringBuilder units = new StringBuilder("My units:\n");
		
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
            //units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

            //if there's enough minerals, train an SCV
            if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50) {
                myUnit.train(UnitType.Terran_SCV);
            }
			
			if (myUnit.getType() == UnitType.Terran_Barracks && self.minerals() >= 50) {
				myUnit.train(UnitType.Terran_Marine);
			}		

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker() && myUnit.isIdle()) {
                Unit closestMineral = null;

                //find the closest mineral
                for (Unit neutralUnit : game.neutral().getUnits()) {
                    if (neutralUnit.getType().isMineralField()) {
                        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                            closestMineral = neutralUnit;
                        }
                    }
                }

                //if a mineral patch was found, send the worker to gather it
                if (closestMineral != null) {
                    myUnit.gather(closestMineral, false);
                }
            }
			
			if (myUnit.getType() == UnitType.Terran_SCV) {
				if (myUnit.exists()){
					workers.add(myUnit);
				}
			}
			
			if ((myUnit.getType() == UnitType.Terran_Marine) ) {
				if (myUnit.exists()){
					marines.add(myUnit);
				}
			}
			
				
			if (myUnit.getType() == UnitType.Terran_Bunker) {
				if (myUnit.exists()){
					bunkers.add(myUnit);
				}
			}
			
			if (myUnit.getType() == UnitType.Terran_Barracks) {
				if (myUnit.exists()){
					barracks.add(myUnit);
				}
			}
			
			if ((bBunker == null) && (workers.size()>9) && (workers.get(9).exists() )){			
				bBunker = workers.get(9);
				game.drawTextMap(bBunker.getPosition(), "Bunker Builder");
				//bunkerBuilder.move(BWTA.getNearestChokepoint(bunkerBuilder.getPosition()).getCenter());
			}
			
			if ((bBarrack == null) && (workers.size()>10) && (workers.get(10).exists() )){			
				bBarrack= workers.get(10);
				game.drawTextMap(bBarrack.getPosition(), "SupplyBuilder");
			}
			
			if (myUnit.isUnderAttack()) {
				positionToAttack = myUnit.getPosition();
			}			     		
        }
		
		//if we're running out of supply and have enough minerals ...
		if ((self.supplyTotal() - self.supplyUsed() < 8) && (self.minerals() >= 100)) {
			//iterate over units to find a worker
			for (Unit myUnit : workers) {
				if (myUnit.getType() == UnitType.Terran_SCV) {
					//get a nice place to build a supply depot
					TilePosition buildTile =
						getBuildTile(myUnit, UnitType.Terran_Supply_Depot, self.getStartLocation());
					//and, if found, send the worker to build it (and leave others alone - break;)
					if (buildTile != null) {
						myUnit.build(UnitType.Terran_Supply_Depot, buildTile);
						game.drawTextMap(myUnit.getPosition(), "Building Supply");
						break;
					}
				}
			}
		}
		
		if ((bBarrack != null) && (self.minerals() >= 150) && (barracks.size() < 4)) {
			TilePosition buildTile = getBuildTile(bBarrack, UnitType.Terran_Barracks, self.getStartLocation());
			if (buildTile != null) {
				bBarrack.build(UnitType.Terran_Barracks, buildTile);
				game.drawTextMap(bBarrack.getPosition(), "Building Barrack");
			}
		}
	

		if ((bBunker != null) && (self.minerals() >= 100)  && (barracks.size() >= 1) && (bunkers.size() <= 3)) {
			if (bunkers.size() < maxBunkers ) {
				TilePosition buildTile = getBuildTile(bBunker, UnitType.Terran_Bunker, self.getStartLocation());
				if (buildTile != null) {
				bBunker.build(UnitType.Terran_Bunker, buildTile);
				game.drawTextMap(bBunker.getPosition(), "Building Bunker");
				}
			}
		}	
	
		for (Unit marine : marines){			
			if (mDefenders.size() <= maxDefenders) {
				mDefenders.add(marine);
				game.drawTextMap(marine.getPosition(), "Defender");
			}	else {
				mAttackers.add(marine);
				game.drawTextMap(marine.getPosition(), "Attacker");
				if ((mAttackers.size() >= minAttackers) && defenseOK) {
					gogogo = true;
				}
			}
		}

		
		for (Unit bunker : bunkers){
			if (bunker != null && bunker.getLoadedUnits().size() < 4) {
				for (Unit marine : mDefenders){
					bunker.load(marine);
					game.drawTextMap(marine.getPosition(), "Defender");//because sometimes it doesn't tag the marine if an attacker is loaded before
				}
			}
			//if we go out of this case the marines belong to the marines list before
			if  ((bunker.getLoadedUnits().size() == 4) && (bunkers.size() == maxBunkers)) {
				defenseOK = true;//wait until 11 attackers have been trained
			}
		}
		
		if (gogogo){
			for (Unit marine : mAttackers){
				if (positionToAttack == null){
					Position basePosition = new Position( (game.mapWidth()- self.getStartLocation().getX())* 32, (game.mapHeight()-self.getStartLocation().getY()) * 32);
					if (marine.getDistance(basePosition) < 100){
						marine.move(randomPosition());
					}	else {
						marine.move(basePosition); 
					}
				}else{
					for (Unit enemy : game.getUnitsInRadius(positionToAttack.getX(), positionToAttack.getY(), 50)){
						if (enemy != null) {
							marine.attack(enemy);
						}
					}
				}
			}
		}
			
						
        //draw my units on screen
        //game.drawTextScreen(10, 25, units.toString());
		game.drawTextScreen(10, 20, "supply : " + self.supplyTotal() + " used: " + self.supplyUsed());
		game.drawTextScreen(10, 30, "workers : " + workers.size());	
		game.drawTextScreen(10, 40,  "marines : " + marines.size() + " defenders : " + mDefenders.size() + " attackers : " + mAttackers.size());
		game.drawTextScreen(10, 50, "bunkers : " + bunkers.size());
		game.drawTextScreen(10, 60,  "defenseOK : " + defenseOK + " gogogo : " + gogogo);
    }
	
	//Get a valid random position in pixel space
	public Position randomPosition(){
		int x = 32*(int)(Math.random() * (float)game.mapWidth());
		int y = 32*(int)(Math.random() * (float)game.mapHeight());
		
		return new Position(x, y);
	}
	
	// Returns a suitable TilePosition to build a given building type near
	// specified TilePosition aroundTile, or null if not found. (builder parameter is our worker)
	public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
		TilePosition ret = null;
		int maxDist = 3;
		int stopDist = 40;

		// Refinery, Assimilator, Extractor
		if (buildingType.isRefinery()) {
			for (Unit n : game.neutral().getUnits()) {
				if ((n.getType() == UnitType.Resource_Vespene_Geyser) &&
						( Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist ) &&
						( Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist )
						) return n.getTilePosition();
			}
		}

		while ((maxDist < stopDist) && (ret == null)) {
			for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; i++) {
				for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; j++) {
					if (game.canBuildHere(new TilePosition(i,j), buildingType, builder, false)) {
						// units that are blocking the tile
						boolean unitsInWay = false;
						for (Unit u : game.getAllUnits()) {
							if (u.getID() == builder.getID()) continue;
							if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) unitsInWay = true;
						}
						if (!unitsInWay) {
							return new TilePosition(i, j);
						}
						// creep for Zerg
						if (buildingType.requiresCreep()) {
							boolean creepMissing = false;
							for (int k=i; k<=i+buildingType.tileWidth(); k++) {
								for (int l=j; l<=j+buildingType.tileHeight(); l++) {
									if (!game.hasCreep(k, l)) creepMissing = true;
									break;
								}
							}
							if (creepMissing) continue;
						}
					}
				}
			}
			maxDist += 2;
		}

		if (ret == null) game.printf("Unable to find suitable build position for "+buildingType.toString());
		return ret;
	}

    public static void main(String[] args) {
        new TestBot1().run();
    }
}
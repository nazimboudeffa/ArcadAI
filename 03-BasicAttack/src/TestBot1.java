import java.util.ArrayList;
import java.util.List;
import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
	
	Unit bunkerBuilder = null;
	
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit discovered " + unit.getType());
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
		List<Unit> marines = new ArrayList<>();
		List<Unit> mAttackers = new ArrayList<>();
		List<Unit> barracks = new ArrayList<>();
		List<Unit> bunkers = new ArrayList<>();
		Position attackedPosition = null;
		Position positionToAttack = null;

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
				workers.add(myUnit);
			}
			
			if (myUnit.getType() == UnitType.Terran_Marine) {
				if (marines.size() < 12) {
					marines.add(myUnit);
					game.drawTextMap(myUnit.getPosition(), "Defender");				
				} else {
					mAttackers.add(myUnit);
					game.drawTextMap(myUnit.getPosition(), "Attacker");
				}
			}
				
			if (myUnit.getType() == UnitType.Terran_Bunker) {
				bunkers.add(myUnit);
			}
			
			if (myUnit.getType() == UnitType.Terran_Barracks) {
				barracks.add(myUnit);
			}
			
			if ((bunkerBuilder == null) && (workers.size()>8)){
				bunkerBuilder = workers.get(8);
				game.drawTextMap(bunkerBuilder.getPosition(), "Bunker Builder");
				bunkerBuilder.move(BWTA.getNearestChokepoint(bunkerBuilder.getPosition()).getCenter());
			}
			
			//all attackers in the visible area
			if (myUnit.isUnderAttack() && myUnit.canAttack()) {
                //attackedPosition = myUnit.getPosition();
                 for (Unit enemyAttacker : game.enemy().getUnits()) {
                    if (enemyAttacker.isAttacking()) {
                        //positionToAttack = enemyAttacker.getPosition();
                        for (Unit marine : marines){
                            if (!marine.isAttacking() && !marine.isMoving()){
                                //marine.attack(positionToAttack);
								marine.attack(enemyAttacker);
                            }
                        }
                    }
                 }
            }
        }
		
		//if we're running out of supply and have enough minerals ...
		if ((self.supplyTotal() - self.supplyUsed() < 4) && (self.minerals() >= 100)) {
			//iterate over units to find a worker
			for (Unit myUnit : self.getUnits()) {
				if (myUnit.getType() == UnitType.Terran_SCV) {
					//get a nice place to build a supply depot
					TilePosition buildTile =
						getBuildTile(myUnit, UnitType.Terran_Supply_Depot, self.getStartLocation());
					//and, if found, send the worker to build it (and leave others alone - break;)
					if (buildTile != null) {
						myUnit.build(UnitType.Terran_Supply_Depot, buildTile);
						break;
					}
				}
			}
		}
		
		//if we're running out of supply and have enough minerals ...
		if ((workers.size() > 8) && (self.minerals() >= 150)) {
			//iterate over units to find a worker
			for (Unit myUnit : self.getUnits()) {
				if (myUnit.getType() == UnitType.Terran_SCV) {
					//get a nice place to build a supply depot
					TilePosition buildTile =
						getBuildTile(myUnit, UnitType.Terran_Barracks, self.getStartLocation());
					//and, if found, send the worker to build it (and leave others alone - break;)
					if (buildTile != null) {
						myUnit.build(UnitType.Terran_Barracks, buildTile);
						break;
					}
				}
			}
		}
		
		if ((bunkerBuilder != null) && (self.minerals() >= 100)  && (barracks.size() >= 1)) {
			if (bunkers.size() < 3 ) {
				TilePosition buildTile = getBuildTile(bunkerBuilder, UnitType.Terran_Bunker, self.getStartLocation());
				if (buildTile != null) {
				bunkerBuilder.build(UnitType.Terran_Bunker, buildTile);
				game.drawTextMap(bunkerBuilder.getPosition(), "Building Bunker");
				}
			} else {
				TilePosition buildTile = getBuildTile(bunkerBuilder, UnitType.Terran_Supply_Depot, self.getStartLocation());
				if (buildTile != null) {
					bunkerBuilder.build(UnitType.Terran_Supply_Depot, buildTile);
					game.drawTextMap(bunkerBuilder.getPosition(), "Building Supply");
				}
			}
		}
		
		for (Unit bunker : bunkers){
			if (bunker != null && bunker.getLoadedUnits().size() < 4 ) {
				for (Unit marine : marines){
					marine.load(bunker);
				}
			}
		}
		
		if ((mAttackers.size()) == 10){
			Position basePosition = new Position( (game.mapWidth()- self.getStartLocation().getX())* 32, (game.mapHeight()-self.getStartLocation().getY()) * 32);
			for (Unit marine :  mAttackers){
				if (marine.getDistance(basePosition) < 100)
					marine.attack(randomPosition());
				else
					marine.attack(basePosition); 
			}
		}		
						
        //draw my units on screen
        //game.drawTextScreen(10, 25, units.toString());
		game.drawTextScreen(10, 20, "supply : " + self.supplyTotal() + " used: " + self.supplyUsed());
		game.drawTextScreen(10, 30, "workers : " + workers.size() );
		game.drawTextScreen(10, 40, "marines : " + marines.size() );
		game.drawTextScreen(10, 50, "bunkers : " + bunkers.size() );
		game.drawTextScreen(10, 60, "attackers : " + mAttackers.size() );
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
package model.database;

import util.FileIO;
import util.Package;
import util.Person;
import util.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import model.IModelToViewAdaptor;

/*
 * Class that handles operations to and from the data files and DBMaps
 */

public class Database {
	
	IModelToViewAdaptor viewAdaptor;
	
	private DBMaps dbMaps;
	private DBFileIO dbIO;
	
	private String packageDirName;
	private String currentDirName;
	private String archiveDirName;

	//TODO instantiate viewAdaptor
	public Database(String rootDirName) {
		
//		this.viewAdaptor = viewAdaptor;
		
		this.packageDirName = rootDirName + "/packages";
		this.currentDirName = packageDirName + "/current";
		this.archiveDirName = packageDirName + "/archive";
		
		this.dbMaps = new DBMaps();
		this.dbIO = new DBFileIO();
	}
	
	/*
	 * Function whose start is controlled by the controller
	 * 
	 * Creates new package directories if they do not exist
	 * Reads the current database, initializing the DBMaps
	 */
	public void start() {
		// check if rootFolder and subfolders exist, create if they do not.
		System.out.println("Creating package directories: " + packageDirName + ", " + archiveDirName);
		FileIO.makeDirs(new String[] {packageDirName, currentDirName, archiveDirName});
		
		// read the active package database
		readCurrentDatabase();
	}
	
	/* Checks in a package into the DBMaps and write a new file for the person */
	public void checkInPackage(String personID, Package pkg) {
		// check if package already exists
		long pkgID = pkg.getPackageID();
		if(dbMaps.getPackage(pkgID) != null) {
			System.out.println("Package ID: " + pkgID + " was already checked in.");
			return;
		}
		// modify current DBMaps
		dbMaps.addPackage(personID, pkg);
		// write new person file
		writePersonFile(personID,currentDirName);
	}
	
	/* Sets a check out date for the package */
	public void checkOutPackage(long pkgID) {
		// modify package checkOut date
		Package pkg = dbMaps.getPackage(pkgID);
		if(pkg.getCheckOutDate() != null) {
			System.out.println("Package ID: " + pkgID + " was already checked out.");
			//TODO uncomment when ready and delete above
//			viewAdaptor.displayMessage("This package was previously checked out on " + 
//					pkg.getCheckOutDate().toString());			
			return;
		} 		

		// note that this automatically edits the package in DBMaps
		pkg.setCheckOutDate(new Date());
		
		// write to file
		writePersonFile(dbMaps.getOwnerID(pkgID),currentDirName);
	}
	
	/* Return a list of all persons in the current directory */
	public ArrayList<Person> getAllCurrentPersons() {
		return dbMaps.getAllPersons();
	}
	
	/* returns a list of all packages in the current directory */
	public ArrayList<Package> getAllCurrentPackages() {
		return dbMaps.getAllPackages();
	}

	/*
	 * Returns a list of filtered and sorted packages, according to the filter and sort string
	 * TODO make the filters work
	 * Available filter strings:
	 * 
	 * Available sort strings:
	 */
	
	public ArrayList<Pair<Person,Package>> getEntries(String filter) {
		ArrayList<Pair<Person,Package>> result = dbMaps.getAllEntries();
		
		//TODO Filters & Sorting
		
		return result;
	}
	
	public ArrayList<Person> getPersonList(String searchString) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/*
	 * Adds a person to the database maps and writes a file for the person
	 * 
	 * If the person is already in the archive, their file will be moved and their
	 * data will be read from the archive file.
	 * 
	 * If the person is not in the archive, a new person will be added to the database maps
	 * and a new file will be written for them.
	 * 
	 */
	public void addPerson(Person person) {
		//Check if person is already in the system
		String personID = person.getPersonID();
		if(dbMaps.getPerson(personID) != null) {
			System.out.println("Person (ID: " + personID + ") to be added by database already exists.");
			return;
		} 
		
		//Check if person is in the archive
		HashSet<String> archiveFileNames = new HashSet<String>(FileIO.getFileNamesFromDirectory(archiveDirName));
		HashSet<String> archivePersonIDs = archiveFileNames;
		
		//If person file is in archive, add file to DBMaps and delete archive file
		if(archivePersonIDs.contains(personID)) {
			String archiveFile = archiveDirName + '/' + personID;
			addPersonPackagesFromFile(archiveFile);
			FileIO.deleteFile(archiveFile);
			dbMaps.editPerson(person); //edit the person instead of adding
		} else {
			//If not in the archive, add new person to DBMaps
			dbMaps.addPerson(person);
		}
		
		//Write the new file
		writePersonFile(personID, currentDirName);
		 
	}
	
	/**
	 * Edits a person in the database, editing the DBMaps and
	 * writing the changes to their file
	 * @param newPerson		Person object containing new attributes for the person
	 */
	public void editPerson(Person newPerson) {
		String personID = newPerson.getPersonID();
		if(dbMaps.getPerson(personID) == null) {
			System.out.println("Person (ID: " + personID + ") to be edited by database not found.");
			return;
		}
		
		// edit person in database maps and write to file
		dbMaps.editPerson(newPerson);
		writePersonFile(personID, currentDirName);
		
	}
	
	/*
	 * Moves a person from the current directory to the archive directory and deletes
	 * their information from the DBMaps
	 */
	public void deletePerson(String personID) {
		if(dbMaps.getPerson(personID) == null) {
			System.out.println("Person (ID: " + personID + ") to be deleted by database not found.");
			return;
		}
		
		// move person file to the archive 
		writePersonFile(personID, archiveDirName);
		FileIO.deleteFile(currentDirName + '/' + personID);
		// remove person from DBMaps - must be after reading and writing file
		dbMaps.deletePerson(personID);
		
	}
	
	/*
	 * Writes a json file containing a person object and all of its associated package objects
	 * to the directory indicated by baseDirectory
	 */
	private void writePersonFile(String personID, String baseDirectory) {
		// create the person and package pair from the DBMaps object
		Person person = dbMaps.getPerson(personID);
		ArrayList<Long> packageIDs = dbMaps.getOwnedPackageIDs(personID);
		ArrayList<Package> pkgList = new ArrayList<Package>();
		for (long pkgID: packageIDs) {
			pkgList.add(dbMaps.getPackage(pkgID));
		}
		
		Pair<Person,ArrayList<Package>> dbPair = 
				new Pair<Person,ArrayList<Package>>(person,pkgList);
		
		//write Pair object to file
		String fileName = baseDirectory + '/' + personID;
		try {
			dbIO.writeDatabaseJSONFile(dbPair, fileName);
		} catch (FileNotFoundException e) {
			// TODO send to log
			System.out.println("Failed to find file: " + fileName);	
		}catch(IOException e) {
			//TODO send to log
			System.out.println("Failed to write " + fileName);
			e.printStackTrace();
		}
	}
	
	/*
	 * Reads a json file containing a person object and all of its associated package objects
	 * from fileName into a Pair object
	 */
	private Pair<Person,ArrayList<Package>> readPersonFile(String fileName) {
		Pair<Person,ArrayList<Package>> dbPair = null;
		try {
			 dbPair = dbIO.readDatabaseJSONFile(fileName);
		} catch (FileNotFoundException e) {
			// TODO send to log
			System.out.println("Failed to find file: " + fileName);	
		} catch (IOException e) {
			// TODO send to log
			System.out.println("Failed to read " + fileName);
			e.printStackTrace();
		} 
		
		return dbPair;
		
	}
	
	/*
	 * Initializes the current database by reading all of the files in the current directory
	 * and placing all of their attributes into the database maps
	 */
	private void readCurrentDatabase() {
		ArrayList<String> currentFileNames = FileIO.getFileNamesFromDirectory(currentDirName);
		for (String fileName: currentFileNames) {
			addPersonPackagesFromFile(currentDirName+'/'+fileName);
		}
	}
	
	/*
	 * Reads and adds all of the person and package information from the fileName to database maps
	 * Note: *Does not rewrite the file, make sure that calling function will write file*
	 */
	private void addPersonPackagesFromFile(String fileName) {
		Pair<Person,ArrayList<Package>> dbPair = readPersonFile(fileName);
		Person person = dbPair.first;
		ArrayList<Package> packages = dbPair.second;
		
		
		//TODO check if person and package are already in system? Would not be catastrophic
		dbMaps.addPerson(person);
		for (Package pkg: packages) {
			dbMaps.addPackage(person.getPersonID(), pkg);
		}
	}
		 
	public void importPersonsFromCSV(String fileName) {
		// Get a list of all people in the database
		ArrayList<String> currentPersons = dbMaps.getAllPersonIDs();
		
		// Remove everyone from the database
		for(String personID: currentPersons) {
			deletePerson(personID);
		}
		
		// Get a list of all people in the file
		ArrayList<Person> csvPersons = new ArrayList<Person>();
		ArrayList<Pair<String,String>> failedToRead = new ArrayList<Pair<String,String>>();
		try {
			csvPersons = dbIO.readDatabaseCSVFile(fileName,failedToRead);
		} catch (FileNotFoundException e) {
			// TODO send error message
			System.out.println("Failed to find file: " + fileName);	
		} catch (IOException e) {
			// TODO send error message
			System.out.println("Failed to read " + fileName);
			e.printStackTrace();
		} catch (FileFormatException e) {
			// TODO send error message
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		
		if(failedToRead.size() != 0) {
			String errorMsg = "\nFailed to read the following people: \n"
					+ "Person - reason\n";
			for (Pair<String,String> error: failedToRead) {
				errorMsg = errorMsg + error.first + " - " + error.second + '\n';
			}
			// TODO send error message
			System.out.println(errorMsg);
		}	
		
		// Add all people to the database
		for(Person person: csvPersons) {
			addPerson(person);
		}
	}
	
	public static void main(String[] args) {
		Database db = new Database("testFiles");
		db.start();
		
		System.out.println("Start:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current active entries = " + db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		
		Date now = new Date();
		
		db.importPersonsFromCSV("testFiles/Test Roster.csv");
		
		Package p1 = new Package(123+now.getTime(),"",now);
		Package p2 = new Package(234+now.getTime(),"It's huge.\n Get it out now.",new Date(now.getTime()-100000000));
		Package p3 = new Package(309435+now.getTime(),"",new Date(now.getTime()-200000000));
		Package p4 = new Package(1+now.getTime(),"",new Date(now.getTime()-300000000));
		Package p5 = new Package(2+now.getTime(),"",new Date(now.getTime()-400000000));

		Person navin = new Person("Pathak", "Navin", "np8@rice.edu", "np8");
		Person chris = new Person("Henderson", "Chris", "cwh1@rice.edu", "cwh1");
		Person christopher = new Person("Henderson", "Christopher", "cwh1@rice.edu", "cwh1");
		Person ambi = new Person("Bobmanuel", "Ambi", "ajb6@rice.edu", "ajb6");
		
		db.addPerson(navin);
		db.addPerson(chris);
		db.addPerson(ambi);

		System.out.println("Added Persons:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		
		db.checkInPackage(navin.getPersonID(),p1);
		System.out.println("Checked In1:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		db.checkInPackage(ambi.getPersonID(),p2);
		db.checkInPackage(chris.getPersonID(),p3);
		System.out.println("Checked In3:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		db.checkInPackage(chris.getPersonID(),p4);
		db.checkInPackage(navin.getPersonID(),p5);
		
		System.out.println("Checked In5:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		
		db.checkOutPackage(p5.getPackageID());
		db.checkOutPackage(p4.getPackageID());
		db.checkOutPackage(p2.getPackageID());
		db.checkOutPackage(p5.getPackageID());

		System.out.println("Checked Out:");
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString() + '\n');
		
		db.deletePerson(navin.getPersonID());
		db.editPerson(christopher);
		
		System.out.println("current persons = " + db.getAllCurrentPersons().toString());
		System.out.println("current entries = " +db.getEntries("").toString());
		System.out.println("current packages = " + db.getAllCurrentPackages().toString());
		
	}
	
}
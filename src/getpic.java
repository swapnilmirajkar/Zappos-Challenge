import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class getpic {
	
	// static declarations for the api base calls
	static final String ZAPPOS_API_KEY = "52ddafbe3ee659bad97fcce7c53592916a6bfd73";
	static final String ZAPPOS_API_PRODUCT = "http://api.zappos.com/Product/";
	static final String ZAPPOS_API_KEY_ID = "?key=";
	static final String ZAPPOS_API_SEPARATOR = "&&";
	
	/**
	 * Command Line Interface program
	 * @param inargs
	 */
	public static void main(String[] inargs) {
		boolean VERBOSE_OUTPUT = false;
		
		// check if any arguments are given
		if (inargs.length == 0) {
			printHelp();
			System.exit(0);
		} else if (inargs[0].equals("-v") || inargs[0].equals("--verbose")) {
			VERBOSE_OUTPUT = true;
		}
	
		
		// create underlying directory structure
		File imgdir = new File("images");
		if (!imgdir.exists()) imgdir.mkdir();
		
		// loop through the arguments to get the files that contain SKU #'s
		ArrayList<File> skuFiles = new ArrayList<File>();
		for (String skuFileStr : inargs) {
			// skip option
			if (skuFileStr.equals("-v") || skuFileStr.equals("--verbose")) continue;
			
			// create file handle and check if file exists
			File skuFile = new File(skuFileStr);
			if (skuFile.exists()) {
				skuFiles.add(skuFile);
			} else {
				System.err.println(" Error: The file " + skuFileStr + " does not exist.\n");
			}
		}
		
		// populate the list of SKU numbers that we need to download the images for
		ArrayList<String> skuNumbers = new ArrayList<String>();
		for (int i = 0; i < skuFiles.size(); i++) {
			
			// read each of the files from input
			try {
				String line;
				BufferedReader br = new BufferedReader(new FileReader(skuFiles.get(i)));
				
				// get rid of leading and trailing spaces and add to our list
				while ((line = br.readLine()) != null) {
					skuNumbers.add(line.trim());
				}
				br.close();
				
			} catch (FileNotFoundException e) {
				System.err.println(" Error: The SKU file has not been found");
			} catch (IOException e) {
				System.err.println(" Error: I/O exception");
			}

		}
		
		// retrieve an image for each of the (valid) SKU numbers
		int successCount = 0;
		int errorCount = 0;
		for (int i = 0; i < skuNumbers.size(); i++) {
			
			// get the list of possible image URLs
			String skunumber = skuNumbers.get(i);
			String URLlist[] = retrieveImageURLs(skunumber);
			if (URLlist == null) {
				errorCount++;
				if (VERBOSE_OUTPUT) System.err.println(" Error: URL for SKU number " + 
						(skunumber.equals("") ? "(blank)" : skunumber) + " could not be found");
				continue;
			}
			
			// try the images from the list of possible URLs until we hit the jackpot
			File imgFile = null;
			jackpot: for (String URLstr : URLlist) {
				if (URLstr != null) {
					imgFile = downloadFileFromURL(URLstr);
					if (imgFile.exists()) {
						successCount++;
						if (VERBOSE_OUTPUT) System.out.println(" Victory is mine! Image for SKU number " + 
								skunumber + " was created");
						break jackpot;
					}
				}
			}
		}
		
		// summary of results
		System.out.println();
		System.out.println(" Summary: " + 
				successCount + " images were downloaded, " + errorCount + " images were not found");
	}
	
	/**
	 * Given a product SKU number, return a list of strings holding the possible
	 * URLs for the default image of the said product
	 * @param skuNumber
	 * @return List of image URLs
	 */
	public static String[] retrieveImageURLs(String skuNumber) {
		
		// open the URL connection to the REST api
		try {
			URL url = new URL(
				ZAPPOS_API_PRODUCT + skuNumber + ZAPPOS_API_KEY_ID + ZAPPOS_API_KEY);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setAllowUserInteraction(false);

			// bad news: the URL is not right. We have to play the guessing game!
			if (conn.getResponseCode() != 200) {
				return fixSKU(skuNumber);
			}
		
			// buffer the response into a string
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = rd.readLine()) != null) {
				sb.append(line);
			}
			rd.close();
			conn.disconnect();
			
			// parse response and return URL
			String JSONstring = sb.toString();
			String[] imageURLs = imageJSONParse(JSONstring);
			return imageURLs;
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	/**
	 * Given a JSON object of the response after searching a product, return a list with the 
	 * default images of the products in the response.
	 * @param ImageJSON
	 * @return
	 */
	public static String[] imageJSONParse(String ImageJSON) {
		JSONObject JSONresponse;
		try {
			JSONresponse = new JSONObject(ImageJSON);
			JSONArray JSONproducts = JSONresponse.getJSONArray("product");
			String[] imageURLs = new String[JSONproducts.length()];
			for (int i = 0; i < imageURLs.length; i++) {
				imageURLs[i] = ((JSONObject) JSONproducts.get(i)).getString("defaultImageUrl");
			}
			return imageURLs;
			
		} catch (JSONException e) {
			// this might mean that the API sent us a malformed JSON object and that is bad!
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * This function returns the file that has been downloaded for further parsing/checking
	 * but there is no need to use it since the download happens inside of the function. 
	 * @param URLString
	 * @return File downloaded
	 */
	public static File downloadFileFromURL(String URLString) {
		
		// use the same filename for the image than the server
		String[] tokens = URLString.split("/");
		File outputFile = new File("images/" + tokens[tokens.length - 1]);
		
		// download file using Java NIO (Apache commons might be a better option)
		URL imageURL;
		try {
			imageURL = new URL(URLString);
		    ReadableByteChannel rbc = Channels.newChannel(imageURL.openStream());
		    FileOutputStream fos = new FileOutputStream(outputFile);
		    fos.getChannel().transferFrom(rbc, 0, 1 << 24);
		    
		    // clean up and return
		    fos.close();
		    return outputFile;
		    
		} catch (MalformedURLException e) {
			// a bad URL was retrieved from the API, this is really bad!!!
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// this should not happen and if it does it might mean that there are disk errors
			e.printStackTrace();
		} catch (IOException e) {
			// this should not happen and if it does it might mean that there are disk errors
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * In this function we try to fix a bad SKU by "guessing" common typos or malformed SKU numbers
	 * using the almighty regex power.
	 * @param badSKU
	 * @return
	 */
	public static String[] fixSKU(String badSKU) {
		
		// can a SKU "number" contain characters?
		String noLettersSKU = badSKU.replaceAll("\\D", "");
		
		// other fixes could take place here, like common typos (ex. "3" vs "e")
		
		// if we already attempted this fix then quit
		if (noLettersSKU.equals(badSKU)) {
			return null;
			
		// otherwise sacrifice another call to the server for a possible fix
		} else {
			return retrieveImageURLs(noLettersSKU);
		}
	}
	
	/**
	 * Print help for the CLI
	 */
	public static void printHelp() {
		System.out.println(" Usage: getpic [-v] [FILE]...");
		System.out.println(" Download default image for product SKU numbers contained in FILE.");
		System.out.println(" There must be one product per line");
		System.out.println();
		System.out.println(" -v, --verbose		verbose output");
	}
}

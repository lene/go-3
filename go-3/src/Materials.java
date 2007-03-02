import javax.media.j3d.Material;
import javax.vecmath.Color3f;

public class Materials extends Colour {

    private static float SHININESS = 20.f;
    
    public static Material[] materials = {
    	new Material (),				//  EMPTY	//
    	new Material (					//  BLACK	//
    		      new Color3f (0.05f, 0.05f, 0.05f),//  ambient
    		      new Color3f (0,0,0),		//  emissive
    		      new Color3f (0.1f, 0.1f, 0.1f),	//  diffuse
    		      new Color3f (0.8f, 0.8f, 0.8f),	//  specular
    		      SHININESS),				//  shininess
    	new Material (					//  WHITE	//
    		      new Color3f (0.3f, 0.3f, 0.3f),	//  ambient
    		      new Color3f (0,0,0),		//  emissive
    		      new Color3f (0.8f, 0.8f, 0.8f),	//  diffuse
    		      new Color3f (1.0f, 1.0f, 1.0f),	//  specular
    		      SHININESS),				//  shininess
    	new Material (					//  RED		//
    		      new Color3f (0.3f, 0.1f, 0.1f),	//  ambient
    		      new Color3f (0,0,0),		//  emissive
    		      new Color3f (0.8f, 0.1f, 0.1f),	//  diffuse
    		      new Color3f (1.0f, 0.8f, 0.8f),	//  specular
    		      SHININESS),				//  shininess
    	new Material (					//  GREEN	//
    		      new Color3f (0.1f, 0.3f, 0.1f),	//  ambient
    		      new Color3f (0,0,0),		//  emissive
    		      new Color3f (0.1f, 0.8f, 0.1f),	//  diffuse
    		      new Color3f (0.8f, 1.0f, 0.8f),	//  specular
    		      SHININESS),				//  shininess
    	new Material (					//  BLUE	//
    		      new Color3f (0.1f, 0.1f, 0.3f),	//  ambient
    		      new Color3f (0,0,0),		//  emissive
    		      new Color3f (0.1f, 0.1f, 0.8f),	//  diffuse
    		      new Color3f (0.8f, 0.8f, 1.0f),	//  specular
    		      SHININESS)				//  shininess
        };

}

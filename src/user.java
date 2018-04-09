
import java.util.ArrayList;
import java.util.List;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author peman
 */
public class user 
{
    Integer name;
    String ip;
    String chavePublica;
    List<String> arquivos = new ArrayList<>();
    
    
    public user (int name,String ip,String chavePublica) 
    {
	this.name = name;
        this.ip = ip;
        this.chavePublica = chavePublica;
    }
    public String toString()
    {
        return this.name.toString();
    }
    
}

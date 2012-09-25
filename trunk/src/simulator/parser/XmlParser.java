/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.parser;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import simulator.Configuration;
import simulator.EventGenerator;
import simulator.Unit;

import java.io.File;
import java.util.HashMap;
import simulator.unit.*;
import simulator.*;

public class XmlParser {

    public static Unit readFile(String fileName) throws Exception{
        File fXmlFile = new File("test.xml");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory
            .newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();

        Unit[] root = readComponent(doc.getDocumentElement(), null);
        if(root.length!=1)
            throw new RuntimeException("Wrong "+root.length);
        return root[0];
    }

    private static Unit[] readComponent(Node node, Unit parent) throws Exception{
        String name = null;
        int count = 1;
        String className = null;
        HashMap<String, String> attributes = new HashMap<String, String>();
        NodeList list =  node.getChildNodes();
        if(list!=null){
            for(int i=0; i<list.getLength(); i++){
                Node tmp = list.item(i);
                if(tmp.getNodeName().equals("name"))
                    name = tmp.getTextContent();
                else if(tmp.getNodeName().equals("count")){
                    System.err.println("count configuration overridden in Configuration.java and is deprecated");
                    //count = Integer.parseInt(tmp.getTextContent());
                }
                else if(tmp.getNodeName().equals("class"))
                    className = tmp.getTextContent();
                else if(tmp.getNodeName().equals("component")){

                }
                else if(tmp.getNodeName().equals("eventGenerator")){

                }
                else{
                    attributes.put(tmp.getNodeName(), tmp.getTextContent());
                }
            }
        }

        if(name==null)
            throw new RuntimeException("no name for "+node);

        if(className==null)
            throw new RuntimeException("no class name for "+node);

        if (name.equalsIgnoreCase("Rack")) count=Configuration.rackCount;
        if (name.equalsIgnoreCase("Machine")) count=Configuration.machinesPerRack;
        if (name.equalsIgnoreCase("Disk")) count = Configuration.disksPerMachine;
        Unit[] units = new Unit[count];

        for(int i=0; i<units.length; i++){
            units[i] = (Unit)Class.forName(className).newInstance();
            units[i].init(name+i, parent, attributes);
        }

        if(list!=null){
            for(int i=0; i<list.getLength(); i++){
                Node tmp = list.item(i);
                if(tmp.getNodeName().equals("component")){
                    for(int j=0; j<units.length; j++){
                        Unit[] children = readComponent(tmp, units[j]);
                        for(int k=0; k<children.length; k++)
                            units[j].addChild(children[k]);
                    }
                }
                else if(tmp.getNodeName().equals("eventGenerator")){
                    for(int j=0; j<units.length; j++){
                        units[j].addEventGenerator(readEventGenerator(tmp));
                    }
                }
            }
        }
        return units;
    }

    private static EventGenerator readEventGenerator(Node node) throws Exception{
        String name = null;
        String className = null;
        HashMap<String, String> attributes = new HashMap<String, String>();
        NodeList list =  node.getChildNodes();
        if(list!=null){
            for(int i=0; i<list.getLength(); i++){
                Node tmp = list.item(i);
                if(tmp.getNodeName().equals("name"))
                    name = tmp.getTextContent();
                else if(tmp.getNodeName().equals("class"))
                    className = tmp.getTextContent();
                else{
                    attributes.put(tmp.getNodeName(), tmp.getTextContent());
                }
            }
        }

        if(name==null)
            throw new RuntimeException("no name for "+node);

        if(className==null)
            throw new RuntimeException("no class name for "+node);

        EventGenerator generator = (EventGenerator)Class.forName(className).newInstance();
        generator.init(name, attributes);
        return generator;
    }
}

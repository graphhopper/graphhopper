package com.graphhopper.search;

import com.carrotsearch.hppc.ByteArrayList;
import com.carrotsearch.hppc.LongArrayList;
import com.graphhopper.Repeat;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.Helper;
import org.junit.Test;

import java.io.*;
import java.util.*;

import static com.graphhopper.search.StringIndex.MAX_UNIQUE_KEYS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class StringIndexAsDatastoreTest {

    private StringIndex create() {
        return new StringIndex(new RAMDirectory()).create(1000);
    }

    @Test
    public void wurst() {
        StringIndex stringIndex = create();
        Map<String, String> payload = new HashMap<>();
        payload.put("knork", "fsrlfihsrliguhslirguhslrgh irgh slriguh slreuig hslreg slrg srelg inhslergiu nsreg ");
        payload.put("pups", "lirtchselrichselritcnuhsleriutnhclsieruntchlsierunhtclseritnuchlsertnuchslerintchselrituchlsiernthc");
        long id = stringIndex.add(payload);
        Map<String, String> all = stringIndex.getAll(id);
        assertEquals(payload, all);
    }

    @Test
    public void pups() throws IOException, ClassNotFoundException {
        StringIndex stringIndex = create();
        Map<String, String> payload = new HashMap<>();
        payload.put("knork", "fsrlfihsrliguhslirguhslrgh irgh slriguh slreuig hslreg slrg srelg inhslergiu nsreg ");
        payload.put("pups", "lirtchselrichselritcnuhsleriutnhclsieruntchlsierunhtclseritnuchlsertnuchslerintchselrituchlsiernthc");


        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(payload);
        out.flush();
        byte[] yourBytes = bos.toByteArray();

        ByteArrayList outBlob = ByteArrayList.from(yourBytes);
        long id = stringIndex.addBinary(Collections.singletonMap("wurst", outBlob));
        ByteArrayList inBlob = stringIndex.getBinary(id, "wurst");
        assertEquals(outBlob, inBlob);

        ByteArrayInputStream bis = new ByteArrayInputStream(inBlob.toArray());
        ObjectInput in = new ObjectInputStream(bis);
        Map<String, String> o = (Map<String, String>) in.readObject();
        assertEquals(payload, o);

    }


}
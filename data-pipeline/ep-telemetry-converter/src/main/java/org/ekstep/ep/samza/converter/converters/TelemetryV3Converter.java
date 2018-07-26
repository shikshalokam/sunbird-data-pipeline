package org.ekstep.ep.samza.converter.converters;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.ekstep.ep.samza.converter.domain.TelemetryV3;
import org.ekstep.ep.samza.converter.exceptions.TelemetryConversionException;
import org.ekstep.ep.samza.reader.NullableValue;
import org.ekstep.ep.samza.reader.Telemetry;
import org.ekstep.ep.samza.reader.TelemetryReaderException;

public class TelemetryV3Converter {

    public static Map<String, String> EDATA_TYPE_MAP = new HashMap<>();
    private static String typeMappingStr = "OE_START:player,GE_START:app,GE_GENIE_START:app,GE_SESSION_START:session,CP_SESSION_START:session,CE_START:editor,GE_SESSION_END:session,CP_SESSION_END:session,OE_END:player,GE_GENIE_END:app,GE_END:app,CE_END:editor";

    static {
        String[] pairTypeArr = typeMappingStr.split(",");
        for (String pair : pairTypeArr) {
            String[] eachTypeArr = pair.split(":");
            EDATA_TYPE_MAP.put(eachTypeArr[0], eachTypeArr[1]);
        }
    }

    private final Map<String, Object> source;
    private final Telemetry reader;

    public TelemetryV3Converter(Map<String, Object> source) {
        this.source = source;
        reader = new Telemetry(source);
    }

    public TelemetryV3[] convert() throws Exception {
        ArrayList<TelemetryV3> v3Events = new ArrayList<>();

        String v2Eid = reader.mustReadValue("eid");
        if ("GE_INTERACT".equals(v2Eid)) {
            v3Events = convertGeInteract();
        } else if ("CE_START".equals(v2Eid)) {
            v3Events = convertCeStart();
        } else if ( "GE_GENIE_START".equals(v2Eid) || "GE_START".equals(v2Eid) ) {
            v3Events = convertGeGenieStart();
        } else {
            TelemetryV3 v3 = new TelemetryV3(reader, source);
            String v3Eid = v3.getEid();
            String eid = reader.<String>mustReadValue("eid");
            v3.setEdata(new EdataConverter(reader).getEdata(v3Eid, eid));
            v3.setTags(source);
            v3Events.add(v3);
        }

        return v3Events.toArray(new TelemetryV3[v3Events.size()]);
    }

    private ArrayList<TelemetryV3> convertGeGenieStart() throws NoSuchAlgorithmException, TelemetryReaderException, TelemetryConversionException{
        // GE_GENIE_START will become START and EXDATA
        TelemetryV3 start = new TelemetryV3(reader, source);
        start.setEid("START");
        start.setEdata(new EdataConverter(reader).getEdata("START", "GE_GENIE_START"));
        start.setTags(source);

        ArrayList<TelemetryV3> events = new ArrayList<>();
        events.add(start);

        Map<String, Object> dspec = (Map<String, Object>) reader.getEdata().get("dspec");

        if (dspec != null && dspec.containsKey("mdata")) {
            TelemetryV3 exData = new TelemetryV3(reader, source);
            exData.setEid("EXDATA");
            exData.setEdata(new EdataConverter(reader).getEdata("EXDATA", "GE_GENIE_START"));
            exData.setTags(source);
            events.add(exData);
        }

        return events;
    }

    private ArrayList<TelemetryV3> convertCeStart() throws NoSuchAlgorithmException, TelemetryReaderException, TelemetryConversionException {
        // CE_START will become START and IMPRESSION
        TelemetryV3 start = new TelemetryV3(reader, source);
        start.setEid("START");
        start.setEdata(new EdataConverter(reader).getEdata("START", "CE_START"));
        start.setTags(source);

        TelemetryV3 impression = new TelemetryV3(reader, source);
        impression.setEid("IMPRESSION");
        impression.setEdata(new EdataConverter(reader).getEdata("IMPRESSION", "CE_START"));
        impression.setTags(source);

        ArrayList<TelemetryV3> events = new ArrayList<>();
        events.add(start);
        events.add(impression);

        return events;
    }

    private ArrayList<TelemetryV3> convertGeInteract() throws TelemetryReaderException, NoSuchAlgorithmException, TelemetryConversionException {
        // edata.eks.subtype = show, generate both IMPRESSION and and LOG
        // Otherwise generate only INTERACT
        ArrayList<TelemetryV3> events = new ArrayList<>();
        NullableValue<String> subType = reader.<String>read("edata.eks.subtype");
        if (!subType.isNull() && "show".equals(subType.value().toLowerCase())) {
            TelemetryV3 impression = new TelemetryV3(reader, source);
            impression.setEid("IMPRESSION");
            impression.setEdata(new EdataConverter(reader).getEdata("IMPRESSION", "GE_INTERACT"));
            impression.setTags(source);

            TelemetryV3 log = new TelemetryV3(reader, source);
            log.setEid("LOG");
            log.setEdata(new EdataConverter(reader).getEdata("LOG", "GE_INTERACT"));
            log.setTags(source);

            events.add(impression);
            events.add(log);
        }

        TelemetryV3 interact = new TelemetryV3(reader, source);
        interact.setEid("INTERACT");
        interact.setEdata(new EdataConverter(reader).getEdata("INTERACT", "GE_INTERACT"));
        interact.setTags(source);
        events.add(interact);

        return events;
    }
}

package pl.vachacz.transcoder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.util.Optional;

public class XmlProcessor implements AutoCloseable {

    private XMLEventReader reader;
    private XMLEvent currentEvent;

    public XmlProcessor(InputStream inputStream) {
        try {
            this.reader = XMLInputFactory.newInstance().createXMLEventReader(inputStream);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    void transcode(EventVisitor eventVisitor) {
        try {
            while (reader.hasNext()) {
                currentEvent = reader.nextEvent();
                eventVisitor.visit();
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    void visitLeaf(String element, LeafVisitor leafVisitor) {
        if (isStartElementOf(element)) {
            consumeLeafUntilFinished(leafVisitor);
        }
    }

    void visitNode(String element, EventVisitor eventVisitor) {
        if (isStartElementOf(element)) {
            consumeNodeUntilFinished(element, eventVisitor, Optional.empty());
        }
    }

    void visitNode(String element, EventVisitor nodeStartedVisitor, EventVisitor eventVisitor, EventVisitor nodeCompletedVisitor) {
        if (isStartElementOf(element)) {
            nodeStartedVisitor.visit();
            consumeNodeUntilFinished(element, eventVisitor, Optional.of(nodeCompletedVisitor));
        }
    }

    private boolean isStartElementOf(String element) {
        return currentEvent.isStartElement() && currentEvent.asStartElement().getName().getLocalPart().equals(element);
    }

    private void consumeNodeUntilFinished(String element, EventVisitor eventVisitor, Optional<EventVisitor> nodeCompletedVisitor) {
        try {
            while (reader.hasNext()) {
                currentEvent = reader.nextEvent();
                if (currentEvent.isEndElement() && currentEvent.asEndElement().getName().getLocalPart().equals(element)) {
                    nodeCompletedVisitor.ifPresent(EventVisitor::visit);
                    return;
                } else {
                    eventVisitor.visit();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void consumeLeafUntilFinished(LeafVisitor consumer) {
        try {
            currentEvent = reader.nextEvent();
            if (currentEvent.isCharacters()) {
                consumer.visit(currentEvent.asCharacters().getData());
            }
            currentEvent = reader.nextEvent();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
    
}

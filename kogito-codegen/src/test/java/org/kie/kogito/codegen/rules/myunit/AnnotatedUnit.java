package org.kie.kogito.codegen.rules.myunit;

import org.kie.kogito.codegen.data.Person;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.DataStore;
import org.kie.kogito.rules.annotations.When;

public class AnnotatedUnit {

    private final DataStore<Person> persons = DataSource.createStore();

    public DataStore<Person> getPersons() {
        return persons;
    }

    public void someRule(@When("/persons[ age >= 18 ]") Person p) {
        System.out.printf("%s is adult", p.getName());
    }

}

Feature: Rewrite Tables tests

  Scenario: rewrite tables should be empty when none defined
    Then mappings should be empty

# Regexp mapping

  Scenario: stored regexp mapping should be retrieved when one mapping matching
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost"

  Scenario: stored regexp mapping should be retrieved when two mappings matching
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost, regex:(.+)@test"

  Scenario: stored regexp mapping should not be retrieved by another user
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    Then mappings for user "test2" at domain "localhost" should be empty

  Scenario: removing a stored regexp mapping should work
    Given store "(.*)@localhost" regexp mapping for user "test" at domain "localhost"
    And store "(.+)@test" regexp mapping for user "test" at domain "localhost"
    When user "test" at domain "localhost" removes a regexp mapping "(.+)@test"
    Then mappings for user "test" at domain "localhost" should contains only "regex:(.*)@localhost"

  Scenario: storing an invalid regexp mapping should not work
    When store an invalid ".*):" regexp mapping for user "test" at domain "localhost"
    Then a "RecipientRewriteTableException" exception should have been thrown

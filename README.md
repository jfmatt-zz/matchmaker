# MatchmakerWeb

Created summer 2011, funded by an NSF grant.

I'm not sure what the current licensing situation is here; it should be GPL. Contact Prof. Mary Hansen at American University for more information.

MatchmakerWeb takes two CSVs and a configuration file that defines what fields are equivalent between the two data files (i.e., LNAME and LASTNAME) and uses a configurable sequence of fuzzy matching algorithms to look for records that are likely to be the same person, but with misspelled names, misremembered ages, or moved home addresses.

It consists of an applet (frontend; handles uploading the files) and a servlet (backend; uses MySQL to process the files, look for matches, and return a set of results). All communication is handled over HTTP using a custom header-based protocol above the application layer.

Mostly, it's on Github for resume purposes.
